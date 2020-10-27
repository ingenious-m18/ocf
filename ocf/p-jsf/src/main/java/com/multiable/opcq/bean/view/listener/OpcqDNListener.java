package com.multiable.opcq.bean.view.listener;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpResponse;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.multiable.bean.view.ModuleController;
import com.multiable.bean.view.ViewController;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.lib.ConvertLib;
import com.multiable.core.share.lib.ListLib;
import com.multiable.core.share.lib.StringLib;
import com.multiable.core.share.lib.WsLib;
import com.multiable.core.share.meta.pr.CawReport;
import com.multiable.core.share.meta.search.StParameter;
import com.multiable.core.share.restful.base.WsParameter;
import com.multiable.core.share.restful.base.WsType;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.PrintUtil;
import com.multiable.core.share.util.ireport.JRFileType;
import com.multiable.core.share.util.ireport.ReportBaseDto;
import com.multiable.erp.core.bean.listener.MacModuleRecordViewListener;
import com.multiable.erp.core.bean.query.ErpQueryUtil;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.ui.application.FacesAssistant;
import com.multiable.ui.util.FacesUtil;
import com.multiable.web.LookupDecorateEvent;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.WebMessage.MessageType;
import com.multiable.web.WebUtil;
import com.multiable.web.component.edittable.EditTableModel;
import com.multiable.web.component.edittable.interfaces.TableActionListener;
import com.multiable.web.rfws.WsFactory;
import com.multiable.web.util.MessageUtil;

public class OpcqDNListener extends MacModuleRecordViewListener {
	String lastLookupType = "";

	@Override
	public void controllerInitialized(ViewController rootController) {
		controller = (ModuleController) rootController;

		EditTableModel tableModel = getTableModel(getFTName());

		if (tableModel != null) {
			tableModel.addListener(new OcfFooterTableListener());
		}

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
			}
		} else if (event.getComponentId().equals("mainFooter_proId_lookupType")) {
			if (MacUtil.isIn(event.getLookupAction().getLookupType(), "qufooter", "asofooter", "sofooter",
					"dnfooter", "sretfooter", "sifooter")) {
				lastLookupType = "tranId";
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
		}
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

	protected class OcfFooterTableListener implements TableActionListener, Serializable {
		private static final long serialVersionUID = 1L;

		// This is supposed to be called after footer "proId" is filled
		@Override
		public void assignedLookupData(String tableName, int tarIndex, SqlTable lookupResult, int rowIndex) {
			if (MacUtil.isIn(lastLookupType, "tranId")) {
				if (tarIndex == 1) {
					Long proId = lookupResult.getLong(rowIndex, "proId");
					List<Long> idList = ListLib.newList();
					idList.add(proId);
					List<String> extraFieldList = ListLib.newList();
					extraFieldList.add("cardsize");
					SqlTable proTable = ErpQueryUtil.searchData(getBeId(), "pro", idList, extraFieldList);

					if (proTable != null && proTable.size() > 0) {
						String cardSize = proTable.getString(1, "cardsize");

						SqlTable remdn = getEntity().getData("remdn");
						remdn.setString(1, "cardsize", cardSize);

						WebUtil.update("remdn_cardsize");
					}

				}
			} else if (MacUtil.isIn(lastLookupType, "pro")) {
				if (tarIndex == 1) {
					String cardSize = lookupResult.getString(rowIndex, "cardsize");

					SqlTable remdn = getEntity().getData("remdn");
					remdn.setString(1, "cardsize", cardSize);

					WebUtil.update("remdn_cardsize");
				}
			}
		}
	}
}
