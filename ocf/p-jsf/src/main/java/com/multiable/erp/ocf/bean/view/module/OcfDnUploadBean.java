package com.multiable.erp.ocf.bean.view.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.component.UIComponent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.multiable.bean.util.FileUtil;
import com.multiable.bean.view.FrameController;
import com.multiable.core.server.longrequest.LongRequestCache;
import com.multiable.core.server.longrequest.LongRequestLib;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.dto.ge.DmsSetting;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.lib.ConvertLib;
import com.multiable.core.share.meta.search.FormatCond;
import com.multiable.core.share.meta.search.StParameter;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.bean.util.MacBeanUtil;
import com.multiable.erp.core.bean.view.MacViewBean;
import com.multiable.erp.ocf.share.OcfStaticVar.OcfEJB;
import com.multiable.erp.ocf.share.interfaces.local.OcfDnUploadLocal;
import com.multiable.logging.CawLog;
import com.multiable.ui.application.FacesAssistant;
import com.multiable.ui.component.buttonselect.BtnSelectOption;
import com.multiable.ui.util.FacesUtil;
import com.multiable.web.LookupDecorateEvent;
import com.multiable.web.ValidateEvent;
import com.multiable.web.ValueChangeEvent;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.WebMessage.MessageType;
import com.multiable.web.WebUtil;
import com.multiable.web.component.edittable.EditTable;
import com.multiable.web.config.CawDialog;
import com.multiable.web.config.DialogObject;
import com.multiable.web.util.MessageUtil;

@ManagedBean(name = "ocfDnUpload")
@ViewScoped
public class OcfDnUploadBean extends MacViewBean implements Serializable {
	private static final long serialVersionUID = 1L;
	private transient Thread waitThread = null;

	private OcfDnUploadLocal ocfDnUploadEJB;

	private boolean debug = false;

	private File ocrFile = null;
	private String ocrFileName = "";

	private Long beId = 0L;
	private Long attachFile = 0L;

	@PostConstruct
	public void postCnstrInit() {

	}

	@Override
	public void initialized() {
		super.initialized();

		createRecordAction();

		ArrayList<BtnSelectOption> beList = MacBeanUtil.createBeSelectList();
		setVariable("beList", beList);

		beId = getDefBeId();

		if (beList != null) {
			boolean match = false;
			for (BtnSelectOption comb : beList) {
				if (ConvertLib.toLong(comb.getValue()) == beId) {
					match = true;
					break;
				}
			}
			if (!match) {
				if (beList != null && beList.size() > 0) {
					beId = ConvertLib.toLong(beList.get(0).getValue());
				}
			}
		}

		if (controller instanceof FrameController) {
			((FrameController) controller).setBeId(beId);
		}

		changeBe();

		ocfDnUploadEJB = null;
		try {
			ocfDnUploadEJB = JNDILocator.getInstance().lookupEJB(OcfEJB.OcfDnUploadEJB, OcfDnUploadLocal.class);
		} catch (Exception e) {
			CawLog.error(e);
		}

		attachFile = 0L;

		DmsSetting ds = CawGlobal.getDmsSetting();
		setVariable("attSize", ds.getAttSize());
		setVariable("attSizeUnit", ds.getAttSizeUnit());

		int maxSize = ds.getAttSize();
		if (ds.getAttSizeUnit().equals("MB")) {
			setVariable("maxSize", maxSize * 1024);
		}
	}

	@Override
	public void lookupParameter(LookupDecorateEvent event) {
		super.lookupParameter(event);
		StParameter param = (StParameter) event.getSource();
		String compId = event.getComponentId();

		List<FormatCond> appendConds = param.getAppendConds();
	}

	@Override
	public boolean validateValue(ValidateEvent ve) {

		return true;
	}

	@Override
	public void valueChange(ValueChangeEvent vce) {
		super.valueChange(vce);
		UIComponent component = vce.getComponent();
		String id = component.getId();
		if (id.equals("beList")) {
			setBeId(ConvertLib.toLong(vce.getNewValue()));

			if (controller instanceof FrameController) {
				((FrameController) controller).setBeId(ConvertLib.toLong(vce.getNewValue()));
			}

			MacBeanUtil.setDefBeId(ConvertLib.toLong(vce.getNewValue()));

			changeBe();

			FacesAssistant.getCurrentInstance().execute("window.beId = " + beId + ";");
			WebUtil.getViewData().setBeId(beId);
			WebUtil.update("iafGenForm");
		}
	}

	@Override
	public void actionPerformed(ViewActionEvent vae) {
		super.actionPerformed(vae);
		String id = vae.getComponent().getId();
		String action = vae.getActionCommand();
		if (vae.getComponent() instanceof EditTable) {

		} else {
			if ("curdToolbar".equals(id)) {
				if ("load".equals(action)) {
					browseData("iafGenSetting", false, "readRecordAction");
				} else if ("create".equals(action)) {
					createRecordAction();
				} else if ("save".equals(action)) {
					beforeSaveRecordAction();
					saveRecordAction();
					afterSaveRecordAction();
				} else if ("delete".equals(action)) {
					deleteRecordAction();
				} else if ("saveAs".equals(action)) {
					CawDialog dialog = CawDialog.createDialog("/view/dialog/printSettingSaveAs.faces");
					WebUtil.showDialog(dialog);
				}
			} else if ("readDoc".equals(action)) {
				if (!checkBeforeReadDoc()) {
					MessageUtil.postMessage("Empty file");
					return;
				}

				if (waitThread != null && waitThread.isAlive()) {
					waitThread.interrupt();
				}
				String longReqKey = LongRequestLib.addLrCache();
				waitThread = new Thread(new Runnable() {
					@Override
					public void run() {
						SqlTable uploadResult = ocfDnUploadEJB.readDoc(getAttachFile());

						LongRequestLib.putResult(longReqKey, uploadResult);
					}
				});
				waitThread.start();
				FacesUtil.getAssistant().addCallbackParam("longReqKey", longReqKey);

			} else if ("afterReadDoc".equals(action)) {
				String longReqKey = FacesUtil.getRequestParamMap().get("longReqKey");
				LongRequestCache longReqCache = LongRequestLib.get(longReqKey);
				if (longReqCache != null) {
					SqlTable uploadResult = (SqlTable) longReqCache.getResult();

					CawDialog dialog = new CawDialog("/view/ocf/dialog/ocfDnUploadResultDialog.faces",
							"ocfDnUploadResultDialog");

					dialog.addParam("uploadResult", uploadResult);
					WebUtil.showDialog(dialog);
				}
			} else if ("downloadOcr".equals(action)) {
				if (waitThread != null && waitThread.isAlive()) {
					waitThread.interrupt();
				}
				String longReqKey = LongRequestLib.addLrCache();
				waitThread = new Thread(new Runnable() {
					@Override
					public void run() {
						File ocrPackage = ocfDnUploadEJB.getOcrPackage();
						LongRequestLib.putResult(longReqKey, ocrPackage);
					}
				});
				waitThread.start();
				FacesUtil.getAssistant().addCallbackParam("longReqKey", longReqKey);
			} else if ("afterDownloadOcr".equals(action)) {
				String longReqKey = FacesUtil.getRequestParamMap().get("longReqKey");
				LongRequestCache longReqCache = LongRequestLib.get(longReqKey);
				if (longReqCache != null) {
					ocrFile = (File) longReqCache.getResult();
					if (ocrFile != null) {
						ocrFileName = ocrFile.getName();
					}

					FacesAssistant.getCurrentInstance().execute(" $('.downloadOcrAction').click();");
				}
			}
		}
	}

	@Override
	public void dialogCallback(ViewActionEvent vae) {
		super.dialogCallback(vae);
		CawDialog dialog = (CawDialog) vae.getSource();
		String dialogName = dialog.getDialogName();
		if ("readRecordAction".equals(dialogName)) {
			if (dialog.getCloseStatus() == DialogObject.DialogStatus.OK) {
				JSONObject obj = (JSONObject) dialog.getReturn().getResult();
				if (obj != null && obj.containsKey("id")) {
					setVariable("curdSetId", ConvertLib.toLong(obj.get("id")));
					readRecordAction();
					afterReadRecordAction();
				}
			}
		} else if ("printSettingSaveAs".equals(dialogName)) {
			if (dialog.getCloseStatus() == DialogObject.DialogStatus.OK) {
				String code = (String) dialog.getReturn().getResult();
				setVariable("curdSetId", 0L);
				setVariable("curdCode", code);

				beforeSaveRecordAction();
				saveRecordAction();
				afterSaveRecordAction();
			}
		}
	}

	private void browseData(String lookupType, boolean multiSelect, String dialogName) {
		CawDialog dialog = new CawDialog("/view/dialog/lookup.faces", dialogName);

		dialog.addRequestParam("sourceId", dialogName);
		dialog.addRequestParam("beId", getBeId());
		dialog.addRequestParam("lookupType", lookupType);
		dialog.addRequestParam("multiSelect", multiSelect);
		dialog.addRequestParam("from", "lookupAddon");
		dialog.addRequestParam("pvs", FacesUtil.getViewState());

		WebUtil.showDialog(dialog);
	}

	public void createRecordAction() {
		setVariable("curdSetId", 0L);
		setVariable("curdCode", "");
		setVariable("curdDesc", "");

		WebUtil.update("mainView");
	}

	public void readRecordAction() {

	}

	public void afterReadRecordAction() {

	}

	public void beforeSaveRecordAction() {

	}

	public void saveRecordAction() {
		long setId = ConvertLib.toLong(getVariable("curdSetId"));
		SqlEntity entity;
		if (setId > 0) {
			entity = MacBeanUtil.readModuleEntity("iafGenSetting", setId);
		} else {
			entity = MacBeanUtil.createModuleEntity("iafGenSetting");
		}
		if (entity == null) {
			return;
		}
		SqlTable iafgensetting = entity.getMainData();
		iafgensetting.setValue(1, "code", ConvertLib.toString(getVariable("curdCode")));
		iafgensetting.setValue(1, "desc", ConvertLib.toString(getVariable("curdDesc")));
		iafgensetting.setValue(1, "beId", getBeId());
		iafgensetting.setValue(1, "settingInfo", JSON.toJSONString(getVariable("iafGenSettingDto")));

		setId = MacBeanUtil.saveModuleEntity("iafGenSetting", entity, true);
		if (setId > 0) {
			setVariable("curdSetId", setId); // success

			readRecordAction();
		}
		WebUtil.update(new String[] { "curdPanel", "mainView" });
	}

	public void afterSaveRecordAction() {

	}

	public void deleteRecordAction() {
		long setId = ConvertLib.toLong(getVariable("curdSetId"));
		if (setId == 0) {
			MessageUtil.postNotice(MessageType.ERROR, CawGlobal.getMess("deleteFail"));
			return;
		}
		boolean status = MacBeanUtil.deleteModuleRecord("iafGenSetting", setId, true);
		if (status) {
			createRecordAction(); // success
		}
		WebUtil.update("mainView");
	}

	public void changeBe() {

	}

	public boolean checkBeforeReadDoc() {
		if (getAttachFile() == 0) {
			return false;
		}

		return true;
	}

	public void downloadOcrAction() {
		try (FileInputStream fio = new FileInputStream(ocrFile)) {
			FileUtil.download(fio, ocrFileName);

		} catch (Exception e) {
			CawLog.logException(e);
		}
		ocrFile = null;
		ocrFileName = "";
	}

	public long getBeId() {
		return beId;
	}

	public void setBeId(long beId) {
		this.beId = beId;
	}

	public Long getAttachFile() {
		return attachFile;
	}

	public void setAttachFile(Long attachFile) {
		this.attachFile = attachFile;
	}
}
