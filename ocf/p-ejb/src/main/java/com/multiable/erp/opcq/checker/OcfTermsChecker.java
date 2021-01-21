package com.multiable.erp.opcq.checker;

import java.util.Date;
import java.util.List;

import javax.naming.NamingException;

import com.multiable.core.ejb.checker.base.CheckRange;
import com.multiable.core.ejb.checker.base.CheckType;
import com.multiable.core.ejb.checker.base.EntityCheck;
import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.ejb.eao.curd.SeDeleteParam;
import com.multiable.core.ejb.eao.curd.SeReadParam;
import com.multiable.core.ejb.eao.curd.SeSaveParam;
import com.multiable.core.ejb.eao.localinterface.SqlEntityEAOLocal;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.dto.SeCurdKey;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.message.CheckMsg;
import com.multiable.core.share.message.CheckMsgLib;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.opcq.share.message.OcfSiError;
import com.multiable.opcq.share.message.OcfTermsError;

public class OcfTermsChecker {

	@EntityCheck(type = CheckType.DELETE, range = CheckRange.BEFORE, checkOrder = 50)
	public CheckMsg checkUsedTerms(SeDeleteParam param) {

		StringBuilder sb = new StringBuilder();
		sb.append(" select 1 from cus where ocfTerms = '" + param.getEntityId() + "'");
		sb.append(" union");
		sb.append(" select 1 from maindn where ocfTerms = '" + param.getEntityId() + "'");
		sb.append(" union");
		sb.append(" select 1 from maintar where ocfTerms = '" + param.getEntityId() + "'");
		sb.append(";");

		SqlTable srResult = CawDs.getResult(sb.toString());
		if (srResult != null && srResult.size() > 0) {
			CheckMsg msg = CheckMsgLib.createMsg(OcfTermsError.FAIL_USED_TERMS, "");
			return msg;
		}

		return null;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 60, actionBreaker = true)
	public CheckMsg checkBalance(SeSaveParam param) {
		SqlEntity siEntity = param.getSqlEntity();
		SqlTable mainTable = siEntity.getData("maintar");

		// Step 1: Get Sales Invoice Point Earned
		double sipointearned = 0;
		double amt = mainTable.getDouble(1, "amt");
		double depoAmt = mainTable.getDouble(1, "depoAmt");
		sipointearned = MathLib.floor((amt + depoAmt) / 50) * 5;

		// Step 2: Get Sales Invoice Point Spent
		SqlTable sipaym = siEntity.getData("sipaym");
		double sipointspent = 0;

		double redepmtionAmt = 0;

		String idStr = "";
		for (int i = 1; i <= sipaym.size(); i++) {
			idStr = idStr + sipaym.getLong(i, "accId") + ",";
		}

		SqlTable accResult = null;
		if (idStr.length() > 0) {
			idStr = idStr.substring(0, idStr.length() - 1);

			String sql = "select id, `coaSetId`, redemptioncreditaccount from chacc where id in (" + idStr
					+ ") and redemptioncreditaccount = 1;";
			accResult = CawDs.getResult(sql);
		}

		if (accResult != null && accResult.size() > 0) {
			TableStaticIndexAdapter accIndex = new TableStaticIndexAdapter(accResult) {
				@Override
				public String getIndexKey() {
					return src.getValueStr(srcRow, "id");
				}
			};
			accIndex.action();

			for (int i = 1; i <= sipaym.size(); i++) {
				int seekRow = accIndex.seek(sipaym.getLong(i, "accId") + "");
				if (seekRow > 0) {
					redepmtionAmt += sipaym.getDouble(i, "amt");
				}
			}
		}

		sipointspent = redepmtionAmt * 2;

		// Step 3: Get Customer data
		Long siId = mainTable.getLong(1, "id");
		Long cusId = mainTable.getLong(1, "cusId");

		if (cusId != 0) {
			String sql = " select * from ocfremcust where hId = " + cusId
					+ " and concat(`sourceType`, '_',`sourceId`) != 'siso_" + siId + "';";
			sql += " select * from ocfrccus where hId = " + cusId + ";";
			sql += " select * from ocfrcdcus where hId = " + cusId
					+ " and concat(`rcdsourcetype`, '_',`rcdsourcetransaction`) != 'siso_" + siId + "';";

			List<SqlTable> resultList = CawDs.getResults(sql);

			SqlTable ocfremcust = resultList.get(0);
			SqlTable ocfrccus = resultList.get(1);
			SqlTable ocfrcdcus = resultList.get(2);

			// Step 4: Calculate Balance

			// Customer pointearned
			double pointearned = 0d;
			for (int i = 1; i <= ocfremcust.size(); i++) {
				pointearned += ocfremcust.getDouble(i, "pointearned");
			}

			// Customer pointspent
			double pointspent = 0d;
			for (int i = 1; i <= ocfrccus.size(); i++) {
				pointspent += ocfrccus.getDouble(i, "rcpointspent");
			}

			for (int i = 1; i <= ocfrcdcus.size(); i++) {
				pointspent += ocfrcdcus.getDouble(i, "rcdpointspent");
			}

			// Step 5: Check balance
			double balance = 0d;
			balance = pointearned - pointspent + sipointearned - sipointspent;

			if (balance >= 0) {
				// Do nothing
			} else {
				CheckMsg msg = CheckMsgLib.createMsg(OcfSiError.FAIL_INSUF_BALANCE,
						CawGlobal.getMess(OcfSiError.FAIL_INSUF_BALANCE.getMess()));
				return msg;
			}

		}

		return null;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 200)
	public CheckMsg insertPointEarned(SeSaveParam param) {

		CheckMsg msg = null;

		// Step 1: Find cus
		SqlEntity entity = param.getSqlEntity();
		// SqlTable mainTable = entity.getMainData();
		SqlTable mainTable = entity.getData("maintar");

		Long cusId = mainTable.getLong(1, "cusId");

		// Step 1b. Read customer record
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(cusId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		// Find ocfremcust
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		// Step 2: Check if pointearn table has source transaction (Delete it)
		// Loop
		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfremcust.size(); i++) {

			if (ocfremcust.getString(i, "sourceType").equals("siso")
					&& ocfremcust.getLong(i, "sourceId") == srcTranId) {
				foundrow = i; // If found
				break;
			}

		}

		// Step 3: Check settled
		// Find amt, depoAmt
		Double amt = mainTable.getDouble(1, "amt");
		Double depoAmt = mainTable.getDouble(1, "depoAmt");
		boolean settled = checkSettled(srcTranId, amt);
		boolean entitledToPts = mainTable.getBoolean(1, "ocfEntitledToPts");

		if (foundrow > 0) {

			// Found, update pointearned, sourcetransactiondate
			Date tDate = (Date) mainTable.getObject(1, "tDate");
			Double pointearned = 0d;

			// Calculation
			if (amt + depoAmt > 50 && settled && entitledToPts) {

				pointearned = MathLib.floor((amt + depoAmt) / 50) * 5;

				// pointearned = (int) (amt + depoAmt)/50 * 5

				ocfremcust.setDouble(foundrow, "pointearned", pointearned);
				ocfremcust.setObject(foundrow, "sourcetransactiondate", tDate);
			} else {
				ocfremcust.deleteRow(foundrow);
			}

		} else {
			if (amt + depoAmt > 50 && settled && entitledToPts) {
				// Not found
				int rec = ocfremcust.addRow();

				// Set date, pointearned, sourcetransactiondate, sourceType, sourceId
				Date tDate = (Date) mainTable.getObject(1, "tDate");
				Double pointearned = 0d;
				pointearned = MathLib.floor((amt + depoAmt) / 50) * 5;

				String sourceType = "siso";
				Long sourceId = mainTable.getLong(1, "id");

				ocfremcust.setObject(rec, "date", tDate);
				ocfremcust.setObject(rec, "sourcetransactiondate", tDate);
				ocfremcust.setDouble(rec, "pointearned", pointearned);
				ocfremcust.setString(rec, "sourceType", sourceType);
				ocfremcust.setLong(rec, "sourceId", sourceId);
			}
		}

		// Step 3a: Yes

		// ocfremcust.setValue(foundrow, "", Xxx);

		// Step 3b: No

		// Calculate total
		calculateTotalPoint(cusEntity);

		// Save customer
		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 201)
	public CheckMsg insertCredit(SeSaveParam param) {
		CheckMsg msg = null;

		// Step 1: Check Account with <Redemption Credit Account> = Y in [Payment Method]
		boolean use = false;
		double redepmtionAmt = 0;

		SqlEntity entity = param.getSqlEntity();
		SqlTable sipaym = entity.getData("sipaym");

		String idStr = "";
		for (int i = 1; i <= sipaym.size(); i++) {
			idStr = idStr + sipaym.getLong(i, "accId") + ",";
		}

		SqlTable accResult = null;
		if (idStr.length() > 0) {
			idStr = idStr.substring(0, idStr.length() - 1);

			String sql = "select id, `coaSetId`, redemptioncreditaccount from chacc where id in (" + idStr
					+ ") and redemptioncreditaccount = 1;";
			accResult = CawDs.getResult(sql);
		}

		if (accResult != null && accResult.size() > 0) {
			TableStaticIndexAdapter accIndex = new TableStaticIndexAdapter(accResult) {
				@Override
				public String getIndexKey() {
					return src.getValueStr(srcRow, "id");
				}
			};
			accIndex.action();

			for (int i = 1; i <= sipaym.size(); i++) {
				int seekRow = accIndex.seek(sipaym.getLong(i, "accId") + "");
				if (seekRow > 0) {
					use = true;
					redepmtionAmt += sipaym.getDouble(i, "amt");
				}
			}
		}

		// Step 2: Find cus
		SqlTable mainTable = entity.getData("maintar");
		Long cusId = mainTable.getLong(1, "cusId");
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(cusId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");

		// Step 3: Check if couponcredit table has source transaction
		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfrcdcus.size(); i++) {

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("siso")
					&& ocfrcdcus.getLong(i, "rcdsourcetransaction") == srcTranId) {
				foundrow = i; // If found
			}

		}

		if (use) {

			// Step 3a: Yes. Update date, amount, pointspent
			if (foundrow > 0) {
				Date tDate = (Date) mainTable.getObject(1, "tDate");

				double pointspent = 0;
				pointspent = redepmtionAmt * 2;

				ocfrcdcus.setObject(foundrow, "rcddate", tDate);
				ocfrcdcus.setDouble(foundrow, "rcdsourcetransactionamount", redepmtionAmt);
				ocfrcdcus.setDouble(foundrow, "rcdpointspent", pointspent);
			} else {
				// Step 3b: No. Insert a new row
				int rec = ocfrcdcus.addRow();

				Date tDate = (Date) mainTable.getObject(1, "tDate");
				double pointspent = 0;
				pointspent = redepmtionAmt * 2;

				ocfrcdcus.setObject(rec, "rcddate", tDate);
				ocfrcdcus.setDouble(rec, "rcdsourcetransactionamount", redepmtionAmt);
				ocfrcdcus.setDouble(rec, "rcdpointspent", pointspent);
				ocfrcdcus.setString(rec, "rcdsourcetype", "siso");
				ocfrcdcus.setLong(rec, "rcdsourcetransaction", srcTranId);
			}

		} else {
			// Step 3c: Yes. Delete the found row
			if (foundrow > 0) {
				ocfrcdcus.deleteRow(foundrow);
			}

			// Step 3d: No. No action

		}

		// Calculate total
		calculateTotalPoint(cusEntity);

		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;
	}

	@EntityCheck(type = CheckType.DELETE, range = CheckRange.AFTER, checkOrder = 200)
	public CheckMsg deletePointEarnedSpent(SeDeleteParam param) {
		CheckMsg msg = null;

		// Step 1: Find cus
		SqlEntity entity = (SqlEntity) param.getJsonParam().get(SeCurdKey.SQLENTITY);
		SqlTable mainTable = entity.getData("maintar");

		Long cusId = mainTable.getLong(1, "cusId");

		// Step 1b. Read customer record
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(cusId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		// Find ocfremcust and delete row if found same transaction
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfremcust.size(); i++) {

			if (ocfremcust.getString(i, "sourceType").equals("siso")
					&& ocfremcust.getLong(i, "sourceId") == srcTranId) {
				foundrow = i; // If found
			}

		}

		if (foundrow > 0) {
			ocfremcust.deleteRow(foundrow);
		}

		// Find ocfrcdcus and delete row if found same transaction
		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");

		foundrow = 0;
		for (int i = 1; i <= ocfrcdcus.size(); i++) {

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("siso")
					&& ocfrcdcus.getLong(i, "rcdsourcetransaction") == srcTranId) {
				foundrow = i; // If found
			}
		}

		if (foundrow > 0) {
			ocfrcdcus.deleteRow(foundrow);
		}

		// Calculate total
		calculateTotalPoint(cusEntity);

		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;

	}

	public void calculateTotalPoint(SqlEntity cusEntity) {
		// Calculate total
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");
		SqlTable ocfrccus = cusEntity.getData("ocfrccus");
		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");
		SqlTable remcus = cusEntity.getData("remcus");

		// Step 1: Calculate total point earned
		double pointearned = 0d;
		for (int i = 1; i <= ocfremcust.size(); i++) {
			pointearned += ocfremcust.getDouble(i, "pointearned");
		}

		// Step 2: Calculate total point spent
		double pointspent = 0d;
		for (int i = 1; i <= ocfrccus.size(); i++) {
			pointspent += ocfrccus.getDouble(i, "rcpointspent");
		}

		for (int i = 1; i <= ocfrcdcus.size(); i++) {
			pointspent += ocfrcdcus.getDouble(i, "rcdpointspent");
		}

		// Step 3: Calculate point balance
		double balance = 0d;
		balance = pointearned - pointspent;

		remcus.setDouble(1, "pointearnedtotal", pointearned);
		remcus.setDouble(1, "pointspenttotal", pointspent);
		remcus.setDouble(1, "pointbalance", balance);
	}

	private boolean checkSettled(Long siId, double siAmt) {
		double settleAmt = 0d;
		StringBuilder sb = new StringBuilder();
		sb.append("select sum(a.amt) as amt");
		sb.append(" from recregt a");
		sb.append(" where a.sTranType = 'siso'");
		sb.append(" and a.sTranId = " + siId);

		SqlTable srResult = CawDs.getResult(sb.toString());
		if (srResult != null && srResult.size() > 0) {
			settleAmt = srResult.getDouble(1, "amt");
		}

		return settleAmt >= siAmt;
	}

}
