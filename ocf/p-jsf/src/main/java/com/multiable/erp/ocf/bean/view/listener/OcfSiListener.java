package com.multiable.erp.ocf.bean.view.listener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;

import com.multiable.bean.view.ModuleAction;
import com.multiable.bean.view.ModuleAction.ActionParam;
import com.multiable.bean.view.ViewController;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.lib.ConvertLib;
import com.multiable.core.share.lib.DateFormatLib;
import com.multiable.core.share.lib.DateLib;
import com.multiable.core.share.lib.ListLib;
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.meta.search.FormatCond;
import com.multiable.core.share.meta.search.StParameter;
import com.multiable.erp.core.bean.decorator.BatchGenDataDecorator;
import com.multiable.erp.core.bean.listener.MacModuleRecordViewListener;
import com.multiable.erp.core.bean.query.ErpQueryUtil;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.erp.ocf.share.util.OcfUtil;
import com.multiable.erp.trdg.bean.decorator.ITradingCalcDecorator;
import com.multiable.erp.trdg.bean.view.IMacCalcDelegate;
import com.multiable.erp.trdg.bean.view.IMacCalculateHelper;
import com.multiable.erp.vat.bean.util.MacVatUtil;
import com.multiable.erp.ztrdgvat.share.util.VatTrdgUtil;
import com.multiable.ui.component.form.inputcombo.ComboOption;
import com.multiable.web.LookupDecorateEvent;
import com.multiable.web.ValueChangeEvent;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.WebUtil;
import com.multiable.web.component.edittable.EditTable;
import com.multiable.web.component.edittable.EditTableModel;
import com.multiable.web.component.edittable.interfaces.TableActionListener;
import com.multiable.web.config.CawDialog;

public class OcfSiListener extends MacModuleRecordViewListener implements BatchGenDataDecorator, ITradingCalcDecorator {

	private String lastLookupType = "";

	@Override
	public void controllerInitialized(ViewController rootController) {
		super.controllerInitialized(rootController);

		EditTableModel tableModel = getTableModel(getFTName());

		if (tableModel != null) {
			tableModel.addListener(new OcfFooterTableListener());
		}
	}

	@Override
	public boolean beforeAction(ModuleAction action) {

		return super.beforeAction(action);
	}

	@Override
	public void afterCreated(ModuleAction action) {
		super.afterCreated(action);
		prepareData(action);
	}

	@Override
	public void afterRead(ModuleAction action) {
		super.afterRead(action);
		if ((boolean) action.getDataOrDefault(ActionParam.status, false)) {
			prepareData(action);
		}
	}

	@Override
	public void afterRefresh(ModuleAction action) {
		super.afterRefresh(action);
		prepareData(action);
	}

	@Override
	public void lookupParameter(LookupDecorateEvent event) {
		StParameter param = (StParameter) event.getSource();

		lastLookupType = "";

		if (event.getComponentId().equals("mainFooter")) {
			if (MacUtil.isIn(event.getLookupAction().getLookupType(), "dnfooter")) {
				lastLookupType = "dnfooter";

				param.addExtraField("dnfooter.hId.multidn.ocfCusPoNo");
				param.addExtraField("dnfooter.hId.multidn.ocfTerms");
			} else if (event.getLookupAction().getLookupType().equals("multidn")) {
				lastLookupType = "multidn";

				param.addExtraField("ocfCusPoNo");
				param.addExtraField("ocfTerms");
			}
		} else if (event.getComponentId().equals("mainFooter_proId_lookupType")) {
			if (MacUtil.isIn(event.getLookupAction().getLookupType(), "dnfooter")) {
				lastLookupType = "dnfooter";

				param.addExtraField("dnfooter.hId.multidn.ocfCusPoNo");
				param.addExtraField("dnfooter.hId.multidn.ocfTerms");
			}
		}
	}

	@Override
	public void valueChange(ValueChangeEvent vce) {
		super.valueChange(vce);
		UIComponent component = vce.getComponent();

		SqlTable mainData = getEntity().getMainData();
		SqlTable art = getEntity().getData("art");

		if (component instanceof EditTable) {
			if (component.getId().equals("mainFooter")) {
				EditTable eTable = (EditTable) vce.getComponent();
				String tableName = eTable.getTableName();
				String columnName = eTable.getCellColumnName();
				EditTableModel tableModel = getTableModel(tableName);
				int rowIndex = tableModel.getRowIndex(eTable.getCellRowid());

				Map<String, Integer> decimapMapping = VatTrdgUtil.getDefDeciMapping(getBeId());
				MacVatUtil.triggerVatFormula(getBeId(), tableName, art, rowIndex, columnName, true, decimapMapping);

				after_a_mainfooter_up(getCalcHelper(), rowIndex);
			}
		} else {
			if (("maintar_cusId").equals(component.getId())) {
				Long newCusId = ConvertLib.toLong(vce.getNewValue());

				// Check if art has dn as source in the first row
				boolean dnSrc = false;
				if (art != null && art.size() > 0) {
					if (art.getString(1, "sourceType").equals("dn") && art.getLong(1, "sourceId") != 0) {
						dnSrc = true;
					}
				}

				if (!dnSrc) {
					// Assign ocfCusPoNo, ocfTerms
					StringBuffer condStr = new StringBuffer();
					condStr.append("#cus#.id = " + newCusId);
					List<FormatCond> conds = ListLib.newList();
					conds.add(ErpQueryUtil.simpleCond(condStr.toString()));
					List<String> extraFieldList = ListLib.newList();
					extraFieldList.add("ocfCusPoNo");
					extraFieldList.add("ocfTerms");
					SqlTable srCusInfo = ErpQueryUtil.searchWsData("cus", extraFieldList, conds);

					if (srCusInfo != null && srCusInfo.size() > 0) {
						mainData.setString(1, "ocfCusPoNo", srCusInfo.getString(1, "ocfCusPoNo"));
						mainData.setString(1, "ocfTerms", srCusInfo.getString(1, "ocfTerms"));
					} else {
						mainData.setString(1, "ocfCusPoNo", "");
						mainData.setString(1, "ocfTerms", "");
					}
					WebUtil.update("maindn_ocfCusPoNo", "maindn_ocfTerms");
				}
			}
		}
	}

	@Override
	public void dialogCallback(ViewActionEvent vae) {
		super.dialogCallback(vae);
		CawDialog dialog = (CawDialog) vae.getSource();
		String dialogName = dialog.getDialogName();
	}

	@Override
	public void after_a_mainfooter_up(IMacCalculateHelper calcHelper, int rec) {
		SqlTable mainTable = getEntity().getMainData();
		SqlTable art = getEntity().getData("art");

		Map<String, Integer> decimapMapping = VatTrdgUtil.getDefDeciMapping(getBeId());
		MacVatUtil.triggerVatFormula(getBeId(), getFTName(), getMainFooter(), rec, "up", true, decimapMapping);

		// Set values for footer ocfProTtl, ocfProDisc
		double ttlProTtl = 0d;
		double ttlProDisc = 0d;
		for (int i : art) {
			double preTaxUp = art.getDouble(i, "preTaxUp");
			double qty = art.getDouble(i, "qty");
			double disc = art.getDouble(i, "disc");

			double proTtl = MathLib.round(preTaxUp * qty, MacUtil.getAmtDecimal(getBeId()));
			double proDisc = MathLib.round(preTaxUp * qty * disc / 100, MacUtil.getAmtDecimal(getBeId()));

			ttlProTtl += proTtl;
			ttlProDisc += proDisc;
			art.setValue(i, "ocfProTtl", proTtl);
			art.setValue(i, "ocfProDisc", proDisc);
		}

		mainTable.setValue(1, "ocfProTtl", ttlProTtl);
		mainTable.setValue(1, "ocfProDisc", ttlProDisc);

		WebUtil.update("maintar_ocfProTtl", "maintar_ocfProDisc");
	}

	private void prepareData(ModuleAction action) {

		// Set up ocfTermsList for ocfTerms
		ArrayList<ComboOption> ocfTermsList = new ArrayList<ComboOption>();
		SqlTable ocfTerms = OcfUtil.getOcfTerms(getBeId());

		ComboOption defOption = new ComboOption();
		defOption.setValue("");
		defOption.setLabel("--");
		defOption.setType("option");

		ocfTermsList.add(defOption);

		if (ocfTerms != null && ocfTerms.size() > 0) {
			for (int i : ocfTerms) {
				ComboOption option = new ComboOption();
				option.setValue("" + ocfTerms.getLong(i, "id"));
				option.setLabel(ocfTerms.getString(i, "desc"));
				option.setType("option");
				ocfTermsList.add(option);
			}
		}
		setVariable("ocfTermsList", ocfTermsList);

		Date ocfLastUploadTime = (Date) getMainData().getObject(1, "ocfLastUploadTime");
		if (DateLib.isEmptyDate(ocfLastUploadTime)) {
			setVariable("ocfLastUploadTimeStr", "");
		} else {
			setVariable("ocfLastUploadTimeStr",
					DateFormatLib.date2Str(ocfLastUploadTime, DateFormatLib.DEF_DATETIME_MILLISECOND_PATTERN));
		}
	}

	// Load from batchGenSetting
	@Override
	public void afterLoadData(SqlTable sourceTable) {
		SqlTable mainTable = getEntity().getMainData();
		SqlTable art = getEntity().getData("art");
		long cusId = mainTable.getLong(1, "cusId");

		// Check if art has dn as source in the first row
		boolean dnSrc = false;
		if (art != null && art.size() > 0) {
			if (art.getString(1, "sourceType").equals("dn") && art.getLong(1, "sourceId") != 0) {
				dnSrc = true;
			}
		}

		if (!dnSrc) {
			// Assign ocfCusPoNo, ocfTerms
			StringBuffer condStr = new StringBuffer();
			condStr.append("#cus#.id = " + cusId);
			List<FormatCond> conds = ListLib.newList();
			conds.add(ErpQueryUtil.simpleCond(condStr.toString()));
			List<String> extraFieldList = ListLib.newList();
			extraFieldList.add("ocfCusPoNo");
			extraFieldList.add("ocfTerms");
			SqlTable srCusInfo = ErpQueryUtil.searchWsData("cus", extraFieldList, conds);

			if (srCusInfo != null && srCusInfo.size() > 0) {
				mainTable.setString(1, "ocfCusPoNo", srCusInfo.getString(1, "ocfCusPoNo"));
				mainTable.setString(1, "ocfTerms", srCusInfo.getString(1, "ocfTerms"));
			} else {
				mainTable.setString(1, "ocfCusPoNo", "");
				mainTable.setString(1, "ocfTerms", "");
			}
		} else {
			// Assign from DN's <Terms>
			StringBuffer condStr = new StringBuffer();
			condStr.append("#maindn#.id = " + art.getLong(1, "sourceId"));
			List<FormatCond> conds = ListLib.newList();
			conds.add(ErpQueryUtil.simpleCond(condStr.toString()));
			List<String> extraFieldList = ListLib.newList();
			extraFieldList.add("ocfCusPoNo");
			extraFieldList.add("ocfTerms");
			SqlTable srCusInfo = ErpQueryUtil.searchWsData("dn", extraFieldList, conds);

			if (srCusInfo != null && srCusInfo.size() > 0) {
				mainTable.setString(1, "ocfCusPoNo", srCusInfo.getString(1, "ocfCusPoNo"));
				mainTable.setString(1, "ocfTerms", srCusInfo.getString(1, "ocfTerms"));
			} else {
				mainTable.setString(1, "ocfCusPoNo", "");
				mainTable.setString(1, "ocfTerms", "");
			}
		}
	}

	public IMacCalculateHelper getCalcHelper() {
		IMacCalculateHelper calcHelper = null;
		if (controller.getVariable("calcDelegate") != null) {
			IMacCalcDelegate delegate = (IMacCalcDelegate) controller.getVariable("calcDelegate");
			calcHelper = delegate.getCalcHelper();
		}

		return calcHelper;
	}

	protected class OcfFooterTableListener implements TableActionListener, Serializable {
		private static final long serialVersionUID = 1L;

		// This is supposed to be called after footer "proId" is filled
		@Override
		public void assignedLookupData(String tableName, int tarIndex, SqlTable lookupResult, int rowIndex) {
			if (tableName.equals("art")) {
				if (lastLookupType.equals("dnfooter")) {
					SqlTable maintar = getEntity().getMainData();

					if (lookupResult.isFieldExist("dnfooter.hId.multidn.ocfCusPoNo")) {
						maintar.setValue(1, "ocfCusPoNo",
								lookupResult.getValue(rowIndex, "dnfooter.hId.multidn.ocfCusPoNo"));
						maintar.setValue(1, "ocfTerms",
								lookupResult.getValue(rowIndex, "dnfooter.hId.multidn.ocfTerms"));
					} else {
						maintar.setValue(1, "ocfCusPoNo", "");
						maintar.setValue(1, "ocfTerms", "");
					}

					WebUtil.update("maintar_ocfCusPoNo", "maintar_ocfTerms");
				} else if (lastLookupType.equals("multidn")) {
					SqlTable maintar = getEntity().getMainData();
					if (lookupResult.isFieldExist("ocfCusPoNo")) {
						maintar.setValue(1, "ocfCusPoNo", lookupResult.getValue(rowIndex, "ocfCusPoNo"));
						maintar.setValue(1, "ocfTerms", lookupResult.getValue(rowIndex, "ocfTerms"));
					} else {
						maintar.setValue(1, "ocfCusPoNo", "");
						maintar.setValue(1, "ocfTerms", "");
					}

					WebUtil.update("maintar_ocfCusPoNo", "maintar_ocfTerms");
				}
			}
		}
	}
}
