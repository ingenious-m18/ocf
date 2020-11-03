package com.multiable.opcq.bean.view.listener;

import com.multiable.bean.view.ModuleAction;
import com.multiable.bean.view.ModuleAction.ActionType;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.bean.listener.MacModuleRecordViewListener;
import com.multiable.logging.CawLog;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OpcqReceiptRegisterLocal;
import com.multiable.ui.application.FacesAssistant;
import com.multiable.web.ViewActionEvent;
import com.multiable.web.WebUtil;
import com.multiable.web.config.CawDialog;
import com.multiable.web.config.DialogObject.DialogStatus;

public class OpcqReceiptRegisterListener extends MacModuleRecordViewListener {
	private boolean chkBal = false;

	@Override
	public boolean beforeAction(ModuleAction action) {

		if (action.getActionType().equals(ActionType.save)) {

			if (!chkBal && !chkBal()) {
				// Prompt a dialog to ask if continue

				WebUtil.askif("ocfChkBal", CawGlobal.getMess("ocf.msgContentDialog"), "");

				return false;
			}
			chkBal = false;

		}

		return super.beforeAction(action);
	}

	@Override
	public void dialogCallback(ViewActionEvent vae) {
		super.dialogCallback(vae);
		CawDialog dialog = (CawDialog) vae.getSource();
		String dialogName = dialog.getDialogName();

		if ("ocfChkBal".equals(dialogName)) {
			if (dialog.getCloseStatus() == DialogStatus.YES) {
				// Continue to save
				chkBal = true;
				FacesAssistant.getCurrentInstance().execute(
						"if( $(\"button[data-action='save']\").length>0 ){ $(\"button[data-action='save']\")[0].click() }");

			} else {
				// No action
			}
		}
	}

	// Check balanc
	public boolean chkBal() {
		// Use EJB
		OpcqReceiptRegisterLocal rrEJB = null;
		try {
			rrEJB = JNDILocator.getInstance().lookupEJB(OcfEJB.OpcqReceiptRegisterEJB,
					OpcqReceiptRegisterLocal.class);
		} catch (Exception e) {
			CawLog.error(e);
		}

		boolean chkResult = rrEJB.chkBal(getEntity());

		return chkResult;
	}
}
