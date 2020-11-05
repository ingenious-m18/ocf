package com.multiable.opcq.bean.view.dialog;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;

import com.multiable.bean.view.DialogController;
import com.multiable.bean.view.ViewBean;
import com.multiable.core.share.data.SqlTable;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.config.DialogObject.DialogStatus;

@ManagedBean(name = "ocfDnUploadResultDialog")
@ViewScoped
public class OcfDnUploadResultDialogBean extends ViewBean {

	private static final long serialVersionUID = 1L;

	private SqlTable uploadResult;

	public DialogController getController() {
		return (DialogController) controller;
	}

	@Override
	public void initialized() {
		uploadResult = (SqlTable) getController().getParam("uploadResult");

	}

	@Override
	public void actionPerformed(ViewActionEvent vae) {
		if (vae.getComponent().getId().equals("btn_ok")) {
			getController().closeDialog(DialogStatus.OK, null);
		}
	}

	public SqlTable getUploadResult() {
		return uploadResult;
	}

	public void setUploadResult(SqlTable uploadResult) {
		this.uploadResult = uploadResult;
	}
}
