package com.multiable.erp.ocf.ejb.checker;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.multiable.core.share.data.SqlTableField;
import com.multiable.core.share.dto.SeCurdKey;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.message.CheckMsg;
import com.multiable.core.share.message.CheckMsgLib;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.erp.ocf.share.message.OcfReceiptRegisterError;

public class OcfReceiptRegisterChecker {

	private List<Long> siList = new ArrayList<>();

	// Cached si used in recregt (in both current and historical)
	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 60)
	public CheckMsg checkSiList(SeSaveParam param) {
		SqlEntity entity = param.getSqlEntity();

		siList.clear();

		StringBuilder sb = new StringBuilder();
		sb.append("select distinct sTranId from recregt");
		sb.append(" where hId = " + param.getEntityId());
		sb.append(" and hId != 0");
		sb.append(" and sTranType = 'siso'");
		SqlTable srResult = CawDs.getResult(sb.toString());

		if (srResult != null && srResult.size() > 0) {
			for (int i : srResult) {
				siList.add(srResult.getLong(i, "sTranId"));
			}
		}

		// Check to see if sTranId in current table recregt exists in siList
		SqlTable recregt = entity.getData("recregt");
		for (int i : recregt) {
			if (recregt.getString(i, "sTranType").equals("siso")
					&& !siList.contains(recregt.getLong(i, "sTranId"))) {

				siList.add(recregt.getLong(i, "sTranId"));
			}
		}

		return null;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 62)
	public CheckMsg checkBalance(SeSaveParam param) {
		SqlEntity rrEntity = param.getSqlEntity();

		SqlTable mainTable = rrEntity.getData("mainrecreg");

		// Step 2: Get Sales Invoice Point Spent
		SqlTable recregdbt = rrEntity.getData("recregdbt");

		double rrpointspent = 0;
		double redepmtionAmt = 0;

		StringBuilder idStr = new StringBuilder();
		for (int i = 1; i <= recregdbt.size(); i++) {
			if (idStr.length() > 0) {
				idStr.append(",");
			}
			idStr.append(recregdbt.getLong(i, "accId"));
		}

		SqlTable accResult = null;
		if (idStr.length() > 0) {
			String sql = "select id, `coaSetId`, redemptioncreditaccount from chacc where id in (" + idStr.toString()
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

			for (int i = 1; i <= recregdbt.size(); i++) {
				int seekRow = accIndex.seek(recregdbt.getLong(i, "accId") + "");
				if (seekRow > 0) {
					redepmtionAmt += recregdbt.getDouble(i, "amt");
				}
			}
		}

		rrpointspent = redepmtionAmt * 2;

		// Step 3: Adjust for pointearned (SI) (In case may be settled by this rr)
		SqlTable recregt = rrEntity.getData("recregt");
		idStr = new StringBuilder();
		Map<Long, Double> amtMap = new HashMap<>();

		for (Long siId : siList) {
			if (idStr.length() > 0) {
				idStr.append(",");
			}
			idStr.append(siId);

			amtMap.put(siId, 0D);
		}

		// The key of amtMap is defined in previous before save method
		for (int i : recregt) {
			if (recregt.getString(i, "sTranType").equals("siso")) {

				amtMap.put(recregt.getLong(i, "sTranId"),
						amtMap.get(recregt.getLong(i, "sTranId")) + recregt.getDouble(i, "amt"));

			}
		}

		// Step 3b: Check to see if amt is fully settled
		// siResult will store the result of SI's settle status, point earned
		SqlTable siResult = null;
		if (idStr.length() > 0) {

			StringBuilder proc = new StringBuilder();
			proc.append("call ocf_chk_si_settled(");
			proc.append(mainTable.getLong(1, "beId"));
			proc.append(", " + param.getEntityId());
			proc.append(", '" + idStr.toString() + "'");
			proc.append(");");

			siResult = CawDs.getResult(proc.toString());
			if (siResult != null) {
				siResult.addField(new SqlTableField("settled", Boolean.class));
				siResult.addField(new SqlTableField("pointEarned", Double.class));

				for (int i : siResult) {
					double unsettledAmt = siResult.getDouble(i, "unsettledAmt");
					double rrAmt = amtMap.containsKey(siResult.getLong(i, "id"))
							? amtMap.get(siResult.getLong(i, "id")) : 0;

					if (rrAmt >= unsettledAmt) {
						siResult.setBoolean(i, "settled", true);
					} else {
						siResult.setBoolean(i, "settled", false);
					}

					double siAmt = siResult.getDouble(i, "amt");
					double siDepoAmt = siResult.getDouble(i, "depoAmt");

					if (siAmt + siDepoAmt > 50 && siResult.getBoolean(i, "settled")
							&& siResult.getBoolean(i, "ocfEntitledToPts")) {
						double pointEarned = MathLib.floor((siAmt + siDepoAmt) / 50) * 5;
						siResult.setValue(i, "pointEarned", pointEarned);
					}
				}
			}
		}

		// Step 3c: Set index
		TableStaticIndexAdapter siResIndex = new TableStaticIndexAdapter(siResult) {
			@Override
			public String getIndexKey() {
				return src.getValueStr(srcRow, "id");
			}
		};
		siResIndex.action();

		// Step 4: Get Customer data
		Long rrId = mainTable.getLong(1, "id");
		Long AIId = mainTable.getLong(1, "AIId");

		if (AIId != 0) {
			String sql = " select * from ocfremcust where hId = " + AIId
					+ " and concat(`sourceType`, '_',`sourceId`) != 'recReg_" + rrId + "';";
			sql += " select * from ocfrccus where hId = " + AIId + ";";
			sql += " select * from ocfrcdcus where hId = " + AIId
					+ " and concat(`rcdsourcetype`, '_',`rcdsourcetransaction`) != 'recReg_" + rrId + "';";

			List<SqlTable> resultList = CawDs.getResults(sql);

			SqlTable ocfremcust = resultList.get(0);
			SqlTable ocfrccus = resultList.get(1);
			SqlTable ocfrcdcus = resultList.get(2);

			// Step 5: Calculate Balance

			// Step 5a: Customer pointearned
			double pointearned = 0d;

			for (int i = 1; i <= ocfremcust.size(); i++) {

				// If the source Type = 'siso', we use siResult, else we use the customer data
				if (ocfremcust.getString(i, "sourceType").equals("siso")) {
					if (siResIndex != null) {
						int seekRow = siResIndex.seek("" + ocfremcust.getLong(i, "sourceId"));
						if (seekRow > 0) {
							pointearned += siResult.getDouble(seekRow, "pointEarned");
						} else {
							pointearned += ocfremcust.getDouble(i, "pointearned");
						}
					} else {
						pointearned += ocfremcust.getDouble(i, "pointearned");
					}

				} else {

					pointearned += ocfremcust.getDouble(i, "pointearned");
				}
			}

			// If ocfremcust does not have the si in the current recregt
			for (int i : recregt) {
				if (!recregt.getString(i, "sTranType").equals("siso")) {
					continue;
				}

				boolean found = false;
				Long c_tranId = recregt.getLong(i, "sTranId");
				int seekRow = siResIndex.seek("" + c_tranId);

				for (int j : ocfremcust) {
					if (ocfremcust.getString(j, "sourceType").equals("siso")
							&& ocfremcust.getLong(j, "sourceId") == c_tranId) {
						found = true;
						break;
					}
				}

				if (!found && seekRow > 0) {
					pointearned += siResult.getDouble(seekRow, "pointEarned");
				}
			}

			// Customer pointspent
			double pointspent = 0d;
			for (int i = 1; i <= ocfrccus.size(); i++) {
				pointspent += ocfrccus.getDouble(i, "rcpointspent");
			}

			for (int i = 1; i <= ocfrcdcus.size(); i++) {
				pointspent += ocfrcdcus.getDouble(i, "rcdpointspent");
			}

			// Step 6: Check balance
			double balance = 0d;
			balance = pointearned - pointspent - rrpointspent;

			if (balance >= 0) {
				// DO NOTHING
			} else {
				CheckMsg msg = CheckMsgLib.createMsg(OcfReceiptRegisterError.FAIL_INSUF_BALANCE,
						CawGlobal.getMess("ocf.insufRedemBalMsg"));
				return msg;
			}

		}

		return null;

	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 201)
	public CheckMsg insertCredit(SeSaveParam param) {
		CheckMsg msg = null;

		// Step 1: Check Account with <Redemption Credit Account> = Y in [Payment Method]
		boolean use = false;
		double redepmtionAmt = 0;

		SqlEntity entity = param.getSqlEntity();
		SqlTable recregdbt = entity.getData("recregdbt");

		String idStr = "";
		for (int i = 1; i <= recregdbt.size(); i++) {
			idStr = idStr + recregdbt.getLong(i, "accId") + ",";
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

			for (int i = 1; i <= recregdbt.size(); i++) {
				int seekRow = accIndex.seek(recregdbt.getLong(i, "accId") + "");
				if (seekRow > 0) {
					use = true;
					redepmtionAmt += recregdbt.getDouble(i, "amt");
				}
			}
		}

		// Step 2: Find cus
		SqlTable mainTable = entity.getData("mainrecreg");
		Long AIId = mainTable.getLong(1, "AIId");
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(AIId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");

		// Step 3: Check if couponcredit table has source transaction
		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfrcdcus.size(); i++) {

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("recReg")
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
				ocfrcdcus.setString(rec, "rcdsourcetype", "recReg");
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

	// For settled SI, if not in cus point earned, will auto-insert
	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 202)
	public CheckMsg insertSettledSi(SeSaveParam param) {
		CheckMsg msg = null;

		// Step 1: Check si in reg
		SqlEntity entity = param.getSqlEntity();
		SqlTable mainTable = entity.getMainData();

		StringBuilder idStr = new StringBuilder();
		for (Long siId : siList) {
			if (idStr.length() > 0) {
				idStr.append(",");
			}
			idStr.append(siId);
		}

		// Step 2: Get SI settle status
		SqlTable siResult = null;
		if (idStr.length() > 0) {
			StringBuilder proc = new StringBuilder();
			proc.append("call ocf_chk_si_settled(");
			proc.append(mainTable.getLong(1, "beId"));
			proc.append(", " + 0);
			proc.append(", '" + idStr.toString() + "'");
			proc.append(");");

			siResult = CawDs.getResult(proc.toString());

			if (siResult != null) {
				siResult.addField(new SqlTableField("settled", Boolean.class));
				siResult.addField(new SqlTableField("pointEarned", Double.class));

				for (int i : siResult) {
					double unsettledAmt = siResult.getDouble(i, "unsettledAmt");

					if (unsettledAmt >= 0) {
						siResult.setBoolean(i, "settled", true);
					} else {
						siResult.setBoolean(i, "settled", false);
					}

					double siAmt = siResult.getDouble(i, "amt");
					double siDepoAmt = siResult.getDouble(i, "depoAmt");

					if (siAmt + siDepoAmt > 50 && siResult.getBoolean(i, "settled")
							&& siResult.getBoolean(i, "ocfEntitledToPts")) {
						double pointEarned = MathLib.floor((siAmt + siDepoAmt) / 50) * 5;
						siResult.setValue(i, "pointEarned", pointEarned);
					}
				}
			}
		}

		// Step 3: Find cus
		Long AIId = mainTable.getLong(1, "AIId");
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(AIId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		// Step 4: Update ocfremcust in Customer
		if (siResult != null) {
			for (int i : siResult) {
				Long siId = siResult.getLong(i, "id");
				int foundrow = 0;
				for (int j = 1; j <= ocfremcust.size(); j++) {

					if (ocfremcust.getString(j, "sourceType").equals("siso")
							&& ocfremcust.getLong(j, "sourceId") == siId) {
						foundrow = j; // If found
						break;
					}

				}

				Date tDate = (Date) siResult.getObject(i, "tDate");
				Double pointearned = siResult.getDouble(i, "pointEarned");
				boolean settled = siResult.getBoolean(i, "settled");

				if (foundrow > 0) {

					// Found, update pointearned, sourcetransactiondate
					// If settled, update pointearned; pointearned != 0
					if (pointearned != 0 && settled) {
						ocfremcust.setDouble(foundrow, "pointearned", pointearned);
						ocfremcust.setObject(foundrow, "sourcetransactiondate", tDate);
					} else {
						// If not settled, delete ocfremcust row
						ocfremcust.deleteRow(foundrow);
					}

				} else {
					// Not found
					if (pointearned != 0 && settled) {
						int rec = ocfremcust.addRow();

						// Set date, pointearned, sourcetransactiondate, sourceType, sourceId
						String sourceType = "siso";
						Long sourceId = siResult.getLong(i, "id");

						ocfremcust.setObject(rec, "date", tDate);
						ocfremcust.setObject(rec, "sourcetransactiondate", tDate);
						ocfremcust.setDouble(rec, "pointearned", pointearned);
						ocfremcust.setString(rec, "sourceType", sourceType);
						ocfremcust.setLong(rec, "sourceId", sourceId);
					}
				}
			}
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
		SqlTable mainTable = entity.getData("mainrecreg");

		Long AIId = mainTable.getLong(1, "AIId");

		// Step 1b. Read customer record
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(AIId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		// Find ocfremcust and delete row if found same transaction
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfremcust.size(); i++) {

			if (ocfremcust.getString(i, "sourceType").equals("recReg")
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

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("recReg")
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

	// Delete SI's related records from cus
	@EntityCheck(type = CheckType.DELETE, range = CheckRange.AFTER, checkOrder = 202)
	public CheckMsg deleteSiPointEarned(SeDeleteParam param) {
		CheckMsg msg = null;

		// Step 1: Find cus
		SqlEntity entity = (SqlEntity) param.getJsonParam().get(SeCurdKey.SQLENTITY);
		SqlTable mainTable = entity.getData("mainrecreg");
		SqlTable recregt = entity.getData("recregt");

		// Step 2a: Get idStr
		StringBuilder idStr = new StringBuilder();
		for (int i : recregt) {
			if (recregt.getString(i, "sTranType").equals("siso")) {
				if (idStr.length() > 0) {
					idStr.append(",");
				}
				idStr.append(recregt.getLong(i, "sTranId"));
			}
		}

		// Step 2b: Get SI settle status
		SqlTable siResult = null;
		if (idStr.length() > 0) {
			StringBuilder proc = new StringBuilder();
			proc.append("call ocf_chk_si_settled(");
			proc.append(mainTable.getLong(1, "beId"));
			proc.append(", " + 0);
			proc.append(", '" + idStr.toString() + "'");
			proc.append(");");

			siResult = CawDs.getResult(proc.toString());

			if (siResult != null) {
				siResult.addField(new SqlTableField("settled", Boolean.class));
				siResult.addField(new SqlTableField("pointEarned", Double.class));

				for (int i : siResult) {
					double unsettledAmt = siResult.getDouble(i, "unsettledAmt");

					if (unsettledAmt >= 0) {
						siResult.setBoolean(i, "settled", true);
					} else {
						siResult.setBoolean(i, "settled", false);
					}

					double siAmt = siResult.getDouble(i, "amt");
					double siDepoAmt = siResult.getDouble(i, "depoAmt");

					if (siAmt + siDepoAmt > 50 && siResult.getBoolean(i, "settled")
							&& siResult.getBoolean(i, "ocfEntitledToPts")) {
						double pointEarned = MathLib.floor((siAmt + siDepoAmt) / 50) * 5;
						siResult.setValue(i, "pointEarned", pointEarned);
					}
				}
			}
		}

		// Step 3: Find cus
		Long AIId = mainTable.getLong(1, "AIId");
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(AIId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		// Step 4: Update ocfremcust in Customer
		if (siResult != null) {
			for (int i : siResult) {
				Long siId = siResult.getLong(i, "id");
				int foundrow = 0;
				for (int j = 1; j <= ocfremcust.size(); j++) {

					if (ocfremcust.getString(j, "sourceType").equals("siso")
							&& ocfremcust.getLong(j, "sourceId") == siId) {
						foundrow = j; // If found
						break;
					}

				}

				Date tDate = (Date) siResult.getObject(i, "tDate");
				Double pointearned = siResult.getDouble(i, "pointEarned");
				boolean settled = siResult.getBoolean(i, "settled");

				if (foundrow > 0) {

					// Found, update pointearned, sourcetransactiondate
					// If settled, update pointearned; pointearned != 0
					if (pointearned != 0 && settled) {
						ocfremcust.setDouble(foundrow, "pointearned", pointearned);
						ocfremcust.setObject(foundrow, "sourcetransactiondate", tDate);
					} else {
						// If not settled, delete ocfremcust row
						ocfremcust.deleteRow(foundrow);
					}

				} else {
					// Not found
					if (pointearned != 0 && settled) {
						int rec = ocfremcust.addRow();

						// Set date, pointearned, sourcetransactiondate, sourceType, sourceId
						String sourceType = "siso";
						Long sourceId = siResult.getLong(i, "id");

						ocfremcust.setObject(rec, "date", tDate);
						ocfremcust.setObject(rec, "sourcetransactiondate", tDate);
						ocfremcust.setDouble(rec, "pointearned", pointearned);
						ocfremcust.setString(rec, "sourceType", sourceType);
						ocfremcust.setLong(rec, "sourceId", sourceId);
					}
				}
			}
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

}
