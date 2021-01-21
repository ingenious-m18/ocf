package com.multiable.opcq.bean.view.listener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.faces.component.UIComponent;

import com.multiable.bean.view.ModuleAction;
import com.multiable.bean.view.ModuleAction.ActionParam;
import com.multiable.bean.view.ViewController;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.lib.ConvertLib;
import com.multiable.core.share.lib.ListLib;
import com.multiable.core.share.meta.search.FormatCond;
import com.multiable.core.share.meta.search.StParameter;
import com.multiable.erp.core.bean.listener.MacModuleRecordViewListener;
import com.multiable.erp.core.bean.query.ErpQueryUtil;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.opcq.share.util.OcfUtil;
import com.multiable.ui.component.form.inputcombo.ComboOption;
import com.multiable.web.LookupDecorateEvent;
import com.multiable.web.ValueChangeEvent;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.WebUtil;
import com.multiable.web.component.edittable.EditTableModel;
import com.multiable.web.component.edittable.interfaces.TableActionListener;
import com.multiable.web.config.CawDialog;

public class OcfSiListener extends MacModuleRecordViewListener {
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

		if (("maintar_cusId").equals(component.getId())) {
			Long newCusId = ConvertLib.toLong(vce.getNewValue());

			// Assign ocfTerms
			StringBuffer condStr = new StringBuffer();
			condStr.append("#cus#.id = " + newCusId);
			List<FormatCond> conds = ListLib.newList();
			conds.add(ErpQueryUtil.simpleCond(condStr.toString()));
			List<String> extraFieldList = ListLib.newList();
			extraFieldList.add("ocfTerms");
			SqlTable srCusInfo = ErpQueryUtil.searchWsData("cus", extraFieldList, conds);

			if (srCusInfo != null && srCusInfo.size() > 0) {
				mainData.setString(1, "ocfTerms", srCusInfo.getString(1, "ocfTerms"));
			} else {
				mainData.setString(1, "ocfTerms", "");
			}
			WebUtil.update("maintar_ocfTerms");
		}
	}

	@Override
	public void dialogCallback(ViewActionEvent vae) {
		super.dialogCallback(vae);
		CawDialog dialog = (CawDialog) vae.getSource();
		String dialogName = dialog.getDialogName();
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
