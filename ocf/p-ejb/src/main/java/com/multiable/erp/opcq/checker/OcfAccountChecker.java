package com.multiable.erp.opcq.checker;

import com.multiable.core.ejb.checker.base.CheckRange;
import com.multiable.core.ejb.checker.base.CheckType;
import com.multiable.core.ejb.checker.base.EntityCheck;
import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.ejb.eao.curd.SeSaveParam;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.message.CheckMsg;
import com.multiable.core.share.message.CheckMsgLib;
import com.multiable.core.share.message.MsgLocator;
import com.multiable.opcq.share.message.OcfAccountError;

public class OcfAccountChecker {

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 200)
	public CheckMsg checkOcfAccount(SeSaveParam param) {

		CheckMsg msg = null;

		SqlEntity entity = param.getSqlEntity();
		SqlTable chacc = entity.getData("chacc");

		if (chacc.getBoolean(1, "transportchargeaccount")) {

			// Step 1: Check Transport Charge Account
			StringBuilder sb = new StringBuilder();
			sb.append(" select a.id from chacc as a");
			sb.append(" where a.id != 0");
			sb.append(" and a.`coaSetId` = " + chacc.getLong(1, "coaSetId"));
			sb.append(" and a.id != " + param.getEntityId());
			sb.append(" and a.transportchargeaccount = 1");

			SqlTable acTable = CawDs.getResult(sb.toString());

			if (acTable != null && acTable.size() > 0) {
				// Show Error
				msg = CheckMsgLib.createMsg(OcfAccountError.DUP_TRANSCHARGE, "");

				MsgLocator locator = new MsgLocator("chacc", "transportchargeaccount");
				locator.setRow(1);
				msg.getLocators().add(locator);

				return msg;
			}

		}

		return msg;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 201)
	public CheckMsg checkInvoiceDiscountAccount(SeSaveParam param) {

		CheckMsg msg = null;

		SqlEntity entity = param.getSqlEntity();
		SqlTable chacc = entity.getData("chacc");

		if (chacc.getBoolean(1, "invoicediscountaccount")) {

			// Step 1: Check Transport Charge Account
			StringBuilder sb = new StringBuilder();
			sb.append(" select a.id from chacc as a");
			sb.append(" where a.id != 0");
			sb.append(" and a.`coaSetId` = " + chacc.getLong(1, "coaSetId"));
			sb.append(" and a.id != " + param.getEntityId());
			sb.append(" and a.invoicediscountaccount = 1");

			SqlTable acTable = CawDs.getResult(sb.toString());

			if (acTable != null && acTable.size() > 0) {
				// Show Error
				msg = CheckMsgLib.createMsg(OcfAccountError.DUP_INVOICEDISCOUNT, "");

				MsgLocator locator = new MsgLocator("chacc", "invoicediscountaccount");
				locator.setRow(1);
				msg.getLocators().add(locator);

				return msg;
			}

		}

		return msg;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 202)
	public CheckMsg checkTransChargeAndInvDisc(SeSaveParam param) {
		CheckMsg msg = null;

		SqlEntity entity = param.getSqlEntity();
		SqlTable chacc = entity.getData("chacc");

		// Check Transport Charge & Invoice Discount cannot be checked at the same time
		// TODO
		if (chacc.getBoolean(1, "transportchargeaccount") && chacc.getBoolean(1, "invoicediscountaccount")) {

			// Show Error
			msg = CheckMsgLib.createMsg(OcfAccountError.DUP_TRANSCHARGEINVOICEDISCOUNT, "");

			MsgLocator locator1 = new MsgLocator("chacc", "transportchargeaccount");
			MsgLocator locator2 = new MsgLocator("chacc", "invoicediscountaccount");
			locator1.setRow(1);
			locator2.setRow(1);
			msg.getLocators().add(locator1);
			msg.getLocators().add(locator2);

			return msg;
		}

		return msg;

	}
}
