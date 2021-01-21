package com.multiable.opcq.bean.view.listener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.faces.component.UIComponent;

import org.apache.http.HttpResponse;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.multiable.bean.view.ModuleAction;
import com.multiable.bean.view.ModuleAction.ActionParam;
import com.multiable.bean.view.ModuleController;
import com.multiable.bean.view.ViewController;
import com.multiable.core.share.data.ITableAppend;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.data.TableAppendAuto;
import com.multiable.core.share.lib.ConvertLib;
import com.multiable.core.share.lib.ListLib;
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.lib.SqlTableLib;
import com.multiable.core.share.lib.StringLib;
import com.multiable.core.share.lib.WsLib;
import com.multiable.core.share.meta.pr.CawReport;
import com.multiable.core.share.meta.search.FormatCond;
import com.multiable.core.share.meta.search.StParameter;
import com.multiable.core.share.restful.base.WsParameter;
import com.multiable.core.share.restful.base.WsType;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.core.share.util.PrintUtil;
import com.multiable.core.share.util.ireport.JRFileType;
import com.multiable.core.share.util.ireport.ReportBaseDto;
import com.multiable.erp.core.bean.listener.MacModuleRecordViewListener;
import com.multiable.erp.core.bean.query.ErpQueryUtil;
import com.multiable.erp.core.bean.util.MacBeanUtil;
import com.multiable.erp.core.bean.util.MacWebUtil;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.erp.trdg.bean.decorator.ITradingCalcDecorator;
import com.multiable.erp.trdg.bean.view.IMacCalcDelegate;
import com.multiable.erp.trdg.bean.view.IMacCalculateHelper;
import com.multiable.erp.trdg.bean.view.module.DeliveryNoteBean;
import com.multiable.logging.CawLog;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OcfDnLocal;
import com.multiable.opcq.share.util.OcfUtil;
import com.multiable.ui.application.FacesAssistant;
import com.multiable.ui.component.form.inputcombo.ComboOption;
import com.multiable.ui.util.FacesUtil;
import com.multiable.web.LookupDecorateEvent;
import com.multiable.web.ValueChangeEvent;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.WebMessage.MessageType;
import com.multiable.web.WebUtil;
import com.multiable.web.component.edittable.EditTableModel;
import com.multiable.web.component.edittable.interfaces.TableActionListener;
import com.multiable.web.rfws.WsFactory;
import com.multiable.web.util.MessageUtil;

public class OcfDnListener extends MacModuleRecordViewListener implements ITradingCalcDecorator {
	private String lastLookupType = "";
	private final static String D_ZipCodeCharge = "Zip Code Charge";
	private OcfDnLocal dnEJB = null;
	private double cusDiscRate = 0;

	@Override
	public void controllerInitialized(ViewController rootController) {
		super.controllerInitialized(rootController);
		controller = (ModuleController) rootController;

		EditTableModel tableModel = getTableModel(getFTName());

		if (tableModel != null) {
			tableModel.addListener(new OcfFooterTableListener());
		}

		try {
			dnEJB = JNDILocator.getInstance().lookupEJB(OcfEJB.OcfDnEJB, OcfDnLocal.class);
		} catch (Exception e) {
			CawLog.error(e);
		}
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
			if (MacUtil.isIn(event.getLookupAction().getLookupType(), "qufooter", "asofooter", "sofooter",
					"dnfooter", "sretfooter", "sifooter")) {
				lastLookupType = "tranId";
			} else if (MacUtil.isIn(event.getLookupAction().getLookupType(), "trdgpro", "approvedSalesPro")) {
				lastLookupType = "pro";

				param.addExtraField("cardsize");
				param.addExtraField("ocfAddOnItem");
			}
		} else if (event.getComponentId().equals("mainFooter_proId_lookupType")) {
			if (MacUtil.isIn(event.getLookupAction().getLookupType(), "qufooter", "asofooter", "sofooter",
					"dnfooter", "sretfooter", "sifooter")) {
				lastLookupType = "tranId";
			}
		} else if (event.getComponentId().equals("maindn_cusId")) {
			param.addExtraField("ocfTerms");
		}
	}

	@Override
	public void valueChange(ValueChangeEvent vce) {
		super.valueChange(vce);
		UIComponent component = vce.getComponent();
		SqlTable mainData = getEntity().getMainData();
		SqlTable dnt = getEntity().getData("dnt");
		SqlTable dndisc = getEntity().getData("dndisc");
		SqlTable remdn = getEntity().getData("remdn");

		if (("remdn_cardsize").equals(component.getId())) {
			if (ConvertLib.toString(vce.getNewValue()).equals("Small")) {
				remdn.setString(1, "ocfsender",
						"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 11px;\">&nbsp;</span></font></p>");
				remdn.setString(1, "msgcontent",
						"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 10px;\">&nbsp;</span></font></p>");
				remdn.setString(1, "ocfrecipient",
						"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 11px;\">&nbsp;</span></font></p>");

				WebUtil.update("remdn_ocfsender", "remdn_msgcontent", "remdn_ocfrecipient");
			} else if (ConvertLib.toString(vce.getNewValue()).equals("Large")) {
				remdn.setString(1, "ocfsender",
						"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 30px;\">&nbsp;</span></font></p>");
				remdn.setString(1, "msgcontent",
						"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 26px;\">&nbsp;</span></font></p>");
				remdn.setString(1, "ocfrecipient",
						"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 30px;\">&nbsp;</span></font></p>");

				WebUtil.update("remdn_ocfsender", "remdn_msgcontent", "remdn_ocfrecipient");
			}
		} else if (MacUtil.isIn(component.getId(), "remdn_ocfrecipient", "remdn_ocfsender", "remdn_msgcontent")) {

			String sel_fieldName = component.getId().split("_")[1];
			String[] htmlFields = new String[] { "ocfrecipient", "msgcontent", "ocfsender" };
			StringBuilder ocfDisplay = new StringBuilder();
			for (int i = 0; i < htmlFields.length; i++) {
				String field = htmlFields[i];

				if (field.equals(sel_fieldName)) {
					ocfDisplay.append(ConvertLib.toString(vce.getNewValue()));
				} else {
					ocfDisplay.append(remdn.getString(1, field));
				}

				if (i != htmlFields.length - 1) {
					ocfDisplay.append("<br>");
				}
			}

			setVariable("ocfDisplay", ocfDisplay.toString());
			WebUtil.update("ocfDisplayBox");

		} else if (("maindn_cusId").equals(component.getId())) {
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
			WebUtil.update("maindn_ocfTerms");

			// Copy dnt
			SqlTable copy_dnt = dnt.genEmptyTable();

			ITableAppend append = new TableAppendAuto(copy_dnt, dnt) {
				@Override
				public boolean condition() {
					return dnt.getLong(incRow, "proId") != 0;
				}
			};
			append.action();

			// Read Customer's Transport Change and insert record

			// Step 1: To remove existing transport charge acc from dndisc table
			conds = ListLib.newList();
			FormatCond cond = new FormatCond();
			cond.setCondString("transportchargeaccount = 1");
			conds.add(cond);
			SqlTable transAccTable = ErpQueryUtil.searchWsData("account", null, conds);
			Long transAccId = 0L;

			if (transAccTable != null && transAccTable.size() > 0) {
				transAccId = transAccTable.getLong(1, "id");

				// Find if dndisc has transAccId, if yes, remove
				for (int i = dndisc.size(); i > 0; i--) {
					if (transAccId == dndisc.getLong(i, "accId")) {
						dndisc.deleteRow(i);
					}
				}

			}

			// Step 2: Get cusTomer Info, Add. Disc table info
			Date tDate = (Date) mainData.getObject(1, "tDate");
			String zipcode = remdn.getString(1, "zipcode");
			cusDiscRate = 0;

			List<SqlTable> myResults = dnEJB.calcCusDiscount(getBeId(), newCusId, transAccId, tDate, zipcode,
					copy_dnt);

			SqlTable cusInfo = myResults.get(0);
			SqlTable zipCodeInfo = myResults.get(2);
			SqlTable chargeInfo = myResults.get(3);
			double transCharge = 0;
			int beDecimal = MacUtil.getAmtDecimal(getBeId());

			IMacCalcDelegate delegate = (IMacCalcDelegate) controller.getVariable("calcDelegate");
			DeliveryNoteBean curBean = (DeliveryNoteBean) MacBeanUtil.getCurrentBeanInstance("trdgDeliveryNote");

			// Step 3: Assign invoice discount
			if (cusInfo != null && cusInfo.size() > 0) {
				cusDiscRate = cusInfo.getDouble(1, "invoicediscount");
				transCharge = cusInfo.getDouble(1, "transportcharge");

				// Step 4: Assgin discount to footer table
				SqlTable proFooter = myResults.get(1);
				TableStaticIndexAdapter proIndex = new TableStaticIndexAdapter(proFooter) {
					@Override
					public String getIndexKey() {
						return src.getValueStr(srcRow, "proId") + "~^~" + src.getValueStr(srcRow, "unitId")
								+ "~^~" + src.getValueStr(srcRow, "qty");
					}
				};
				proIndex.action();

				for (int i : dnt) {
					double oldDisc = dnt.getDouble(i, "disc");
					int seekRow = proIndex.seek(dnt.getValueStr(i, "proId") + "~^~" + dnt.getValueStr(i, "unitId")
							+ "~^~" + dnt.getValueStr(i, "qty"));
					if (seekRow > 0 && !proFooter.getBoolean(seekRow, "ocfAddOnItem")) {
						dnt.setValue(i, "disc", cusDiscRate);
					} else {
						dnt.setValue(i, "disc", 0);
					}

					delegate.calc_a_mainfooter_disc(i, oldDisc);
					curBean.calc_invamt();
				}
			}

			// Get the row number for transport charge
			int cr_row = 0;
			if (chargeInfo != null && chargeInfo.size() > 0) {
				for (int i : chargeInfo) {
					if (chargeInfo.getLong(i, "accId") == transAccId) {
						cr_row = i;
						break;
					}
				}
			}

			// Step 5: Transport Charge
			if (transCharge != 0 && cr_row > 0) {

				int rec = dndisc.addRow();
				dndisc.setString(rec, "accDesc", chargeInfo.getString(cr_row, "desc"));
				dndisc.setLong(rec, "accId", transAccId);
				dndisc.setString(rec, "aDesc", chargeInfo.getString(cr_row, "desc"));
				dndisc.setString(rec, "c_d", "charge");
				dndisc.setDouble(rec, "discRate", 0);
				dndisc.setDouble(rec, "preTaxAmt", transCharge);
				dndisc.setLong(rec, "taxCodeId", chargeInfo.getLong(cr_row, "taxCodeId"));
				dndisc.setDouble(rec, "vatPer", chargeInfo.getDouble(cr_row, "taxRate"));

				double taxAmt = MathLib.round(transCharge * chargeInfo.getDouble(cr_row, "taxRate") / 100,
						beDecimal);
				double amt = MathLib.round(taxAmt + transCharge, beDecimal);
				dndisc.setDouble(rec, "taxAmt", taxAmt);
				dndisc.setDouble(rec, "amt", amt);
			}

			// Step 6: Zip code
			if (zipCodeInfo != null && zipCodeInfo.size() > 0) {
				double deliCharge = zipCodeInfo.getDouble(1, "deliverychargeperdistance");

				int rec = dndisc.addRow();
				dndisc.setString(rec, "accDesc", D_ZipCodeCharge);
				dndisc.setLong(rec, "accId", transAccId);
				dndisc.setString(rec, "aDesc", chargeInfo.getString(cr_row, "desc"));
				dndisc.setString(rec, "c_d", "charge");
				dndisc.setDouble(rec, "discRate", 0);
				dndisc.setDouble(rec, "preTaxAmt", deliCharge);
				dndisc.setLong(rec, "taxCodeId", chargeInfo.getLong(cr_row, "taxCodeId"));
				dndisc.setDouble(rec, "vatPer", chargeInfo.getDouble(cr_row, "taxRate"));

				double taxAmt = MathLib.round(deliCharge * chargeInfo.getDouble(cr_row, "taxRate") / 100,
						beDecimal);
				double amt = MathLib.round(taxAmt + deliCharge, beDecimal);
				dndisc.setDouble(rec, "taxAmt", taxAmt);
				dndisc.setDouble(rec, "amt", amt);
			}

			curBean.calc_adisc();

			MacWebUtil.reloadEditTable("discFooter");
		} else if (("remdn_zipcode").equals(component.getId())) {
			int beDecimal = MacUtil.getAmtDecimal(getBeId());
			Date tDate = (Date) mainData.getObject(1, "tDate");

			DeliveryNoteBean curBean = (DeliveryNoteBean) MacBeanUtil.getCurrentBeanInstance("trdgDeliveryNote");

			// Step 1: To remove existing transport charge acc from dndisc table
			List<FormatCond> conds = ListLib.newList();
			FormatCond cond = new FormatCond();
			cond.setCondString("transportchargeaccount = 1");
			conds.add(cond);
			SqlTable transAccTable = ErpQueryUtil.searchWsData("account", null, conds);
			Long transAccId = 0L;

			if (transAccTable != null && transAccTable.size() > 0) {
				transAccId = transAccTable.getLong(1, "id");

				// Find if dndisc has transAccId, if yes, remove
				for (int i = dndisc.size(); i > 0; i--) {
					if (transAccId == dndisc.getLong(i, "accId")
							&& dndisc.getString(i, "accDesc").equals(D_ZipCodeCharge)) {
						dndisc.deleteRow(i);
					}
				}

				SqlTable zcCharge = dnEJB.getZipCodeCharge(getBeId(), transAccId, tDate,
						ConvertLib.toString(vce.getNewValue()));
				if (zcCharge != null && zcCharge.size() > 0) {
					double chargeAmt = zcCharge.getDouble(1, "zcCharge");
					int rec = dndisc.addRow();
					dndisc.setString(rec, "accDesc", D_ZipCodeCharge);
					dndisc.setLong(rec, "accId", transAccId);
					dndisc.setString(rec, "aDesc", transAccTable.getString(1, "desc"));
					dndisc.setString(rec, "c_d", "charge");
					dndisc.setDouble(rec, "discRate", 0);
					dndisc.setDouble(rec, "preTaxAmt", chargeAmt);
					dndisc.setLong(rec, "taxCodeId", zcCharge.getLong(1, "taxCodeId"));
					dndisc.setDouble(rec, "vatPer", zcCharge.getDouble(1, "taxRate"));

					double taxAmt = MathLib.round(chargeAmt * zcCharge.getDouble(1, "taxRate") / 100, beDecimal);
					double amt = MathLib.round(taxAmt + chargeAmt, beDecimal);
					dndisc.setDouble(rec, "taxAmt", taxAmt);
					dndisc.setDouble(rec, "amt", amt);
				}

				curBean.calc_adisc();

				MacWebUtil.reloadEditTable("discFooter");
			}
		}
	}

	@Override
	public void actionPerformed(ViewActionEvent vae) {
		super.actionPerformed(vae);
		String command = vae.getActionCommand();
		SqlTable mainTable = getEntity().getMainData();
		SqlTable remdn = getEntity().getData("remdn");

		if (isIn(command, "printCard")) {
			String cardSize = remdn.getString(1, "cardsize");
			String providerCode = "";
			String docReportCode = "";

			if (cardSize.equals("--")) {
				MessageUtil.postMessage(MessageType.ERROR, "Card Size is not selected.");
				return;
			} else {
				if (cardSize.equals("Small")) {
					providerCode = "com.multiable.erp.opcq.ireport.provider.cardsizesmall.CardSizeSmallProvider";
					// Format code
					docReportCode = "@ocf_cardsizesmall_sql";
					// docReportCode = "igntest";
				} else if (cardSize.equals("Large")) {
					providerCode = "com.multiable.erp.opcq.ireport.provider.cardsizelarge.CardSizeLargeProvider";

					docReportCode = "@ocf_cardsizelarge_sql";
					// docReportCode = "ingtest-L";
				}
			}

			if (!StringLib.isEmpty(providerCode) && !StringLib.isEmpty(docReportCode)) {
				printPdf(getBeId(), providerCode, docReportCode, command, mainTable.getLong(1, "id"));
			}
		} else if (command.equals("btn_loadChargeDiscount")) {
			// Read Customer's Transport Change and insert record

			SqlTable dndisc = getEntity().getData("dndisc");

			// Step 1: To remove existing transport charge acc from dndisc table
			List<FormatCond> conds = ListLib.newList();
			FormatCond cond = new FormatCond();
			cond.setCondString("transportchargeaccount = 1");
			conds.add(cond);
			SqlTable transAccTable = ErpQueryUtil.searchWsData("account", null, conds);
			Long transAccId = 0L;

			if (transAccTable != null && transAccTable.size() > 0) {
				transAccId = transAccTable.getLong(1, "id");

				// Find if dndisc has transAccId, if yes, remove
				for (int i = dndisc.size(); i > 0; i--) {
					if (transAccId == dndisc.getLong(i, "accId")) {
						dndisc.deleteRow(i);
					}
				}

			}

			// Step 2: To remove existing invoice discount acc from dndisc table
			// TODO
			// from conds change to condsinv
			List<FormatCond> condsinv = ListLib.newList();
			FormatCond condinv = new FormatCond();
			condinv.setCondString("invoicediscountaccount = 1");
			condsinv.add(condinv);
			SqlTable invDiscAccTable = ErpQueryUtil.searchWsData("account", null, condsinv);
			Long invDiscAccId = 0L;

			if (invDiscAccTable != null && invDiscAccTable.size() > 0) {
				invDiscAccId = invDiscAccTable.getLong(1, "id");

				// Find if dndisc has transAccId, if yes, remove
				for (int i = dndisc.size(); i > 0; i--) {
					if (invDiscAccId == dndisc.getLong(i, "accId")) {
						dndisc.deleteRow(i);
					}
				}

			}

			SqlTable dnt = getEntity().getData("dnt");
			Long cusId = mainTable.getLong(1, "cusId");
			Long beId = mainTable.getLong(1, "beId");
			Date tDate = (Date) mainTable.getObject(1, "tDate");
			String zipcode = remdn.getString(1, "zipcode");
			SqlTable discChargeTable = dnEJB.loadChargeDiscount(beId, cusId, transAccId, invDiscAccId, tDate,
					zipcode, dnt);

			if (discChargeTable != null && discChargeTable.size() > 0) {
				for (int i = 1; i <= discChargeTable.size(); i++) {
					// Copy the info from discChargeTable to dndisc

					SqlTableLib.copyRow(i, discChargeTable, dndisc);
				}

				getCalcHelper().calc_adisc();
			}

			MacWebUtil.reloadEditTable("discFooter");

		}
	}

	@Override
	public void footerDefault(String tableName, int rowIndex) {
		if (tableName.equals("dnt")) {
			SqlTable dnt = getEntity().getData("dnt");
			dnt.setDouble(rowIndex, "qty", 1);
			dnt.setValue(rowIndex, "disc", cusDiscRate);
		}
	}

	private void prepareData(ModuleAction action) {
		SqlTable mainTable = getEntity().getMainData();
		SqlTable remdn = getEntity().getData("remdn");
		cusDiscRate = dnEJB.getCusDiscount(getBeId(), mainTable.getLong(1, "cusId"));

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

		// Set the display box
		String ocfDisplay = "";
		String ocfrecipient = remdn.getString(1, "ocfrecipient");
		String ocfsender = remdn.getString(1, "ocfsender");
		String msgcontent = remdn.getString(1, "msgcontent");

		ocfDisplay = ocfrecipient + "\n" + msgcontent + "\n" + ocfsender;
		setVariable("ocfDisplay", ocfDisplay);
	}

	public HttpResponse printPdf(long beId, String providerCode, String reportCode, String command, long id) {

		CawReport defaultReport = CawGlobal.getReportByProviderCode(providerCode);
		if (defaultReport == null) {
			return null;
		}

		String reportDtoName = defaultReport.getReportDto();
		String moduleName = "dn";
		String menuCode = "dn";
		Set<Long> ids = new HashSet<>();
		ids.add(id);

		ReportBaseDto reportDto = PrintUtil.initReportDtoBySettingInfoJSON(reportDtoName, null, reportCode);
		if (StringLib.isEmpty(reportDto.getReportCode())) {
			reportDto.setReportCode(defaultReport.getCode());
		}

		String progressKey = "progressKey" + StringLib.genUqKey();
		reportDto.setProgressKey(progressKey);
		reportDto.setBeId(beId);
		reportDto.setLangCode(ConvertLib.toString(getController().getVariable("langCode")));
		reportDto.addIdSet(ReportBaseDto.IDMAPMAINKEY, ids);

		reportDto.setFileType(JRFileType.pdf);

		reportDto.setReportTitle("");

		WsParameter param = WsLib.createWsParam("jrPrint/printPdf", WsType.post);
		param.addQueryParam("paramDto", JSON.toJSONString(reportDto));
		param.addQueryParam("dtoClass", reportDtoName);
		param.addQueryParam("menuCode", menuCode);
		param.addQueryParam("skipLog", "true");

		HttpResponse response = WsLib.callWs(param);

		if (WsLib.isResponseOK(response)) {
			String fileType = reportDto.getFileType().toString();
			FacesUtil.getAssistant().addCallbackParam("fileType", fileType);

			JSONObject jsObject = JSONObject.parseObject(WsFactory.resolveResponse(response));
			JSONArray paths = jsObject.getJSONArray("fileNames");
			String noApvInfo = jsObject.getString("noApvInfo");
			String fulCountInfo = jsObject.getString("fulCountInfo");

			if (paths == null || paths.isEmpty()) {
				if (!StringLib.isEmpty(noApvInfo)) {
					MessageUtil.postMessage(MessageType.ERROR, noApvInfo, false, true);
				}

				if (!StringLib.isEmpty(fulCountInfo)) {
					MessageUtil.postMessage(MessageType.ERROR, fulCountInfo, false, true);
				}

			} else {

				if (!StringLib.isEmpty(noApvInfo)) {
					MessageUtil.postMessage(MessageType.INFO, noApvInfo, false, true);
				}

				if (!StringLib.isEmpty(fulCountInfo)) {
					MessageUtil.postMessage(MessageType.INFO, fulCountInfo, false, true);
				}

				if (JRFileType.pdf.equals(reportDto.getFileType())) {
					JSONObject ob = new JSONObject();
					ob.put("beId", beId);
					ob.put("module", moduleName);
					ob.put("providerCode", providerCode);
					ob.put("fileName", paths.toJSONString());
					ob.put("lang", reportDto.getLangCode());
					ob.put("fileDir", jsObject.getString("fileDir"));
					ob.put("fileType", reportDto.getFileType().toString());
					ob.put("columnName", CawGlobal.getMess("fileList"));
					ob.put("allFile", CawGlobal.getMess("allFile"));
					ob.put("menuCode", menuCode);
					ob.put("id", ids.toArray()[0]);
					FacesAssistant.getCurrentInstance()
							.execute("if(myFrame) {myFrame.openPDfViewer(" + ob.toJSONString() + ");}");
				}
			}

		} else {
			WebUtil.showMessageInfo(response, null, "printFail");
		}

		return response;
	}

	// @Override
	// public void after_a_mainfooter_up(IMacCalculateHelper calcHelper, int rec) {
	// Map<String, Integer> decimapMapping = VatTrdgUtil.getDefDeciMapping(getBeId());
	// Map<String, Double> reCalResult = MacVatUtil.triggerVatFormula(getBeId(), getFTName(), getMainFooter(), rec,
	// "qty", true, decimapMapping);
	// if (reCalResult != null && reCalResult.size() > 0) {
	// updateVatCalFields();
	// getCalcHelper().calc_invamt();
	// }
	// }

	// public void updateVatCalFields() {
	// double ttlPreTaxAmt = 0d;
	// double ttlPreTaxCharge = 0d;
	// double ttlPreTaxDisc = 0d;
	// double ttlTaxAmt = 0d;
	//
	// SqlTable mainFooter = getMainFooter();
	// SqlTable adisc = getEntity().getData("dndisc");
	//
	// if (mainFooter != null && mainFooter.size() > 0) {
	// for (int i = 1; i <= mainFooter.size(); i++) {
	// ttlPreTaxAmt += mainFooter.getDouble(i, "preTaxAmt");
	// ttlTaxAmt += mainFooter.getDouble(i, "taxAmt");
	// }
	// getMainData().setValue(1, "preTaxTtlAmt", MathLib.round(ttlPreTaxAmt, MacUtil.getAmtDecimal(getBeId())));
	// WebUtil.update(getMTName() + "_preTaxTtlAmt");
	// }
	//
	// if (adisc != null && adisc.size() > 0) {
	// for (int i : adisc) {
	// if (adisc.isFieldExist("c_d") && adisc.getValueStr(i, "c_d").equals("discount")) {
	// ttlPreTaxDisc += adisc.getDouble(i, "preTaxAmt");
	// ttlTaxAmt -= adisc.getDouble(i, "taxAmt");
	// } else {
	// ttlPreTaxCharge += adisc.getDouble(i, "preTaxAmt");
	// ttlTaxAmt += adisc.getDouble(i, "taxAmt");
	// }
	// }
	//
	// getMainData().setValue(1, "preTaxTtlDisc",
	// MathLib.round(ttlPreTaxDisc, MacUtil.getAmtDecimal(getBeId())));
	// getMainData().setValue(1, "preTaxTtlCharge",
	// MathLib.round(ttlPreTaxCharge, MacUtil.getAmtDecimal(getBeId())));
	// WebUtil.update(getMTName() + "_preTaxTtlDisc", getMTName() + "_preTaxTtlCharge");
	// }
	//
	// getMainData().setValue(1, "preTaxAmt",
	// MathLib.round(ttlPreTaxAmt + ttlPreTaxCharge - ttlPreTaxDisc, MacUtil.getAmtDecimal(getBeId())));
	// getMainData().setValue(1, "taxAmt", MathLib.round(ttlTaxAmt, MacUtil.getAmtDecimal(getBeId())));
	// WebUtil.update(getMTName() + "_preTaxAmt", getMTName() + "_taxAmt");
	// }
	//
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
			SqlTable dnt = getEntity().getData("dnt");
			dnt.setDouble(tarIndex, "qty", 1);
			dnt.setDouble(tarIndex, "qty1", 1);

			if (MacUtil.isIn(lastLookupType, "tranId")) {
				if (tarIndex == 1) {
					Long proId = lookupResult.getLong(rowIndex, "proId");
					List<Long> idList = ListLib.newList();
					idList.add(proId);
					List<String> extraFieldList = ListLib.newList();
					extraFieldList.add("cardsize");
					extraFieldList.add("ocfAddOnItem");
					SqlTable proTable = ErpQueryUtil.searchData(getBeId(), "pro", idList, extraFieldList);

					if (proTable != null && proTable.size() > 0) {
						String cardSize = proTable.getString(1, "cardsize");

						SqlTable remdn = getEntity().getData("remdn");
						remdn.setString(1, "cardsize", cardSize);

						// Default font, paragraph, font size for <Sender>, <Message Content>

						if (cardSize.equals("Small")) {
							remdn.setString(1, "ocfsender",
									"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 11px;\">&nbsp;</span></font></p>");
							remdn.setString(1, "msgcontent",
									"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 10px;\">&nbsp;</span></font></p>");
							remdn.setString(1, "ocfrecipient",
									"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 11px;\">&nbsp;</span></font></p>");

						} else if (cardSize.equals("Large")) {
							remdn.setString(1, "ocfsender",
									"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 30px;\">&nbsp;</span></font></p>");
							remdn.setString(1, "msgcontent",
									"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 26px;\">&nbsp;</span></font></p>");
							remdn.setString(1, "ocfrecipient",
									"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 30px;\">&nbsp;</span></font></p>");
						}

						// Handle add on item
						if (proTable.getBoolean(1, "ocfAddOnItem")) {
							dnt.setValue(tarIndex, "disc", 0);
						} else {
							dnt.setValue(tarIndex, "disc", cusDiscRate);
						}

						WebUtil.update("remdn_cardsize", "remdn_ocfsender", "remdn_msgcontent",
								"remdn_ocfrecipient");

					}

				} else {
					Long proId = lookupResult.getLong(rowIndex, "proId");
					List<Long> idList = ListLib.newList();
					idList.add(proId);
					List<String> extraFieldList = ListLib.newList();
					extraFieldList.add("ocfAddOnItem");
					SqlTable proTable = ErpQueryUtil.searchData(getBeId(), "pro", idList, extraFieldList);

					// Handle add on item
					if (proTable != null && proTable.size() > 0) {
						if (proTable.getBoolean(1, "ocfAddOnItem")) {
							dnt.setValue(tarIndex, "disc", 0);
						} else {
							dnt.setValue(tarIndex, "disc", cusDiscRate);
						}
					}
				}

				//
				// Map<String, Integer> decimapMapping = VatTrdgUtil.getDefDeciMapping(getBeId());
				// MacVatUtil.triggerVatFormula(getBeId(), getFTName(), getMainFooter(), tarIndex, "qty", true,
				// decimapMapping);

			} else if (MacUtil.isIn(lastLookupType, "pro")) {

				if (tarIndex == 1) {
					String cardSize = lookupResult.getString(rowIndex, "cardsize");

					SqlTable remdn = getEntity().getData("remdn");
					remdn.setString(1, "cardsize", cardSize);

					// Default font, paragraph, font size for <Sender>, <Message Content>

					if (cardSize.equals("Small")) {
						remdn.setString(1, "ocfsender",
								"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 11px;\">&nbsp;</span></font></p>");
						remdn.setString(1, "msgcontent",
								"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 10px;\">&nbsp;</span></font></p>");
						remdn.setString(1, "ocfrecipient",
								"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 11px;\">&nbsp;</span></font></p>");
					} else if (cardSize.equals("Large")) {
						remdn.setString(1, "ocfsender",
								"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 30px;\">&nbsp;</span></font></p>");
						remdn.setString(1, "msgcontent",
								"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 26px;\">&nbsp;</span></font></p>");
						remdn.setString(1, "ocfrecipient",
								"<p style=\"text-align: center; \"><font face=\"Arial Black\"><span style=\"font-size: 30px;\">&nbsp;</span></font></p>");
					}

					// Handle add on item
					if (lookupResult.getBoolean(rowIndex, "ocfAddOnItem")) {
						dnt.setValue(tarIndex, "disc", 0);
					} else {
						dnt.setValue(tarIndex, "disc", cusDiscRate);
					}

					WebUtil.update("remdn_cardsize", "remdn_ocfsender", "remdn_msgcontent", "remdn_ocfrecipient");
				}

			}
		}
	}
}
