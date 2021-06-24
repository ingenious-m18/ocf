package com.multiable.erp.ocf.ejb.eao.bean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.data.SqlTableField;
import com.multiable.core.share.lib.DateLib;
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.lib.StringLib;
import com.multiable.core.share.meta.dd.DdColumn;
import com.multiable.core.share.meta.dd.DdTable;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.erp.core.share.util.MacXMLUtil;
import com.multiable.erp.ocf.share.OcfStaticVar.OcfEJB;
import com.multiable.erp.ocf.share.interfaces.local.OcfDnLocal;
import com.multiable.logging.CawLog;

@Stateless(name = OcfEJB.OcfDnEJB)
@Local({ OcfDnLocal.class })
public class OcfDnEJB implements OcfDnLocal {
	@Override
	public SqlTable loadChargeDiscount(Long beId, Long cusId, Long transAccId, Long invDiscAccId, Date tDate,
			String zipcode, SqlTable dnt) {

		// Step 1: Check Transport Charge value, Invoice Discount Account
		StringBuilder sb = new StringBuilder();
		sb.append("select id, transportcharge, invoicediscount from cus where id = " + cusId);

		SqlTable cusTable = CawDs.getResult(sb.toString());

		// Step 2: Initialize return table
		SqlTable discChargeTable = new SqlTable();

		discChargeTable.addField(new SqlTableField("accDesc", String.class));
		discChargeTable.addField(new SqlTableField("accId", Long.class));
		discChargeTable.addField(new SqlTableField("aDesc", String.class));
		discChargeTable.addField(new SqlTableField("c_d", String.class));
		discChargeTable.addField(new SqlTableField("discRate", Double.class));
		discChargeTable.addField(new SqlTableField("preTaxAmt", Double.class));
		discChargeTable.addField(new SqlTableField("taxCodeId", Long.class));
		discChargeTable.addField(new SqlTableField("vatPer", Double.class));
		discChargeTable.addField(new SqlTableField("taxAmt", Double.class));
		discChargeTable.addField(new SqlTableField("amt", Double.class));

		// Step 3: Check accId with Transport Charge Account checked.
		sb = new StringBuilder();
		sb.append("select id, `desc` from chacc where id != 0 and id = " + transAccId);

		SqlTable transAccTable = CawDs.getResult(sb.toString());

		if (transAccTable != null && transAccTable.size() > 0) {

			if (cusTable != null && cusTable.size() > 0) {
				double transportcharge = cusTable.getDouble(1, "transportcharge");

				if (transportcharge > 0) {

					// Step 4: Insert a row for Additional Discount / Charge Table
					int rec = discChargeTable.addRow();
					discChargeTable.setString(rec, "accDesc", transAccTable.getString(1, "desc"));
					discChargeTable.setLong(rec, "accId", transAccId);
					discChargeTable.setString(rec, "aDesc", transAccTable.getString(1, "desc"));
					// TODO
					discChargeTable.setString(rec, "c_d", "charge");
					discChargeTable.setDouble(rec, "discRate", 0);
					discChargeTable.setDouble(rec, "preTaxAmt", cusTable.getDouble(1, "transportcharge"));

					// Get Tax Rate & Tax Code
					SqlTable taxInfo = getAccVatDetails(beId, transAccId, tDate);
					discChargeTable.setLong(rec, "taxCodeId", taxInfo.getLong(1, "taxCodeId"));
					discChargeTable.setDouble(rec, "vatPer", taxInfo.getDouble(1, "taxRate"));

					int beDecimal = MacUtil.getAmtDecimal(beId);
					double taxAmt = MathLib.round(
							cusTable.getDouble(1, "transportcharge") * taxInfo.getDouble(1, "taxRate") / 100,
							beDecimal);
					double amt = MathLib.round(taxAmt + cusTable.getDouble(1, "transportcharge"), beDecimal);
					discChargeTable.setDouble(rec, "taxAmt", taxAmt);
					discChargeTable.setDouble(rec, "amt", amt);

				} else {
					// NO action
				}

			}
		}

		// Step 5: Check accId with Invoice Discount Account checked.

		sb = new StringBuilder();
		sb.append("select id, `desc` from chacc where id != 0 and id = " + invDiscAccId);

		SqlTable invDiscAccTable = CawDs.getResult(sb.toString());

		if (invDiscAccTable != null && invDiscAccTable.size() > 0) {

			if (cusTable != null && cusTable.size() > 0) {
				double invoicediscount = cusTable.getDouble(1, "invoicediscount");

				if (invoicediscount > 0) {

					// Step 6: Insert a row for Additional Discount / Charge Table
					int rec = discChargeTable.addRow();
					discChargeTable.setString(rec, "accDesc", invDiscAccTable.getString(1, "desc"));
					discChargeTable.setLong(rec, "accId", invDiscAccId);
					discChargeTable.setString(rec, "aDesc", invDiscAccTable.getString(1, "desc"));

					discChargeTable.setString(rec, "c_d", "discount");
					discChargeTable.setDouble(rec, "discRate", cusTable.getDouble(1, "invoicediscount"));

					// Get Tax Rate & Tax Code
					SqlTable taxInfo = getAccVatDetails(beId, invDiscAccId, tDate);
					discChargeTable.setLong(rec, "taxCodeId", taxInfo.getLong(1, "taxCodeId"));
					discChargeTable.setDouble(rec, "vatPer", taxInfo.getDouble(1, "taxRate"));

					double ttlProFooter = 0d;
					for (int i = 1; i <= dnt.size(); i++) {
						ttlProFooter += dnt.getDouble(i, "amt");
					}

					int beDecimal = MacUtil.getAmtDecimal(beId);
					double preTaxAmt = MathLib.round(ttlProFooter * cusTable.getDouble(1, "invoicediscount") / 100,
							beDecimal);
					double taxAmt = MathLib.round(preTaxAmt * taxInfo.getDouble(1, "taxRate") / 100, beDecimal);
					double amt = MathLib.round(taxAmt + preTaxAmt, beDecimal);

					discChargeTable.setDouble(rec, "preTaxAmt", preTaxAmt);
					discChargeTable.setDouble(rec, "taxAmt", taxAmt);
					discChargeTable.setDouble(rec, "amt", amt);

				} else {
					// NO action
				}

			}
		}

		// Step 6: Check zip code
		if (!StringLib.isEmpty(zipcode) && zipcode.length() >= 2) {
			sb = new StringBuilder();
			sb.append(" select a.postalcode, a.deliverychargeperdistance");
			sb.append(" from ocfpostalcodedeliverycharget as a, ocfpostalcodedeliverycharge as b");
			sb.append(" where a.hId = b.id and b.id != 0");
			sb.append(" and b.beId = " + beId);
			sb.append(" and a.postalcode = '" + zipcode.substring(0, 2) + "'");
			sb.append("");

			SqlTable postCodeTable = CawDs.getResult(sb.toString());
			if (postCodeTable != null && postCodeTable.size() > 0 && transAccTable != null
					&& transAccTable.size() > 0) {
				for (int i = 1; i <= postCodeTable.size(); i++) {
					int rec = discChargeTable.addRow();
					discChargeTable.setString(rec, "accDesc", transAccTable.getString(1, "desc"));
					discChargeTable.setLong(rec, "accId", transAccId);
					discChargeTable.setString(rec, "aDesc", transAccTable.getString(1, "desc"));

					discChargeTable.setString(rec, "c_d", "charge");
					discChargeTable.setDouble(rec, "discRate", 0);
					discChargeTable.setDouble(rec, "preTaxAmt",
							postCodeTable.getDouble(i, "deliverychargeperdistance"));

					// Get Tax Rate & Tax Code
					SqlTable taxInfo = getAccVatDetails(beId, transAccId, tDate);
					discChargeTable.setLong(rec, "taxCodeId", taxInfo.getLong(1, "taxCodeId"));
					discChargeTable.setDouble(rec, "vatPer", taxInfo.getDouble(1, "taxRate"));

					int beDecimal = MacUtil.getAmtDecimal(beId);
					double taxAmt = MathLib.round(postCodeTable.getDouble(i, "deliverychargeperdistance")
							* taxInfo.getDouble(1, "taxRate") / 100, beDecimal);
					double amt = MathLib.round(taxAmt + postCodeTable.getDouble(i, "deliverychargeperdistance"),
							beDecimal);

					discChargeTable.setDouble(rec, "taxAmt", taxAmt);
					discChargeTable.setDouble(rec, "amt", amt);
				}
			}
		}

		return discChargeTable;

	}

	public SqlTable getAccVatDetails(Long beId, Long accId, Date tDate) {
		SqlTable retTable = new SqlTable();
		retTable.addField(new SqlTableField("taxCodeId", Long.class));
		retTable.addField(new SqlTableField("taxRate", Double.class));
		retTable.addRow();

		StringBuffer sql = new StringBuffer();
		sql.append("select b.taxCodeId, d.taxRate");
		sql.append(" from chacc a");
		sql.append(" inner join actaxsetting b on a.id = b.hId");
		sql.append(" inner join taxcode c on b.taxCodeId = c.id");
		sql.append(" inner join taxcodet d on c.id = d.hId");
		sql.append(" where a.id = " + accId);
		sql.append(" and a.taxNeed = 1");
		sql.append(" and b.vatBeId = " + beId);
		if (tDate != null) {
			sql.append(" and b.effDate <= '" + DateLib.dateToString(tDate) + "'");
			sql.append(" and b.expDate >= '" + DateLib.dateToString(tDate) + "'");
			sql.append(" and d.effDate <= '" + DateLib.dateToString(tDate) + "'");
			sql.append(" and d.expDate >= '" + DateLib.dateToString(tDate) + "'");
		}
		sql.append(" limit 1");
		sql.append(";");

		SqlTable sqlResult = CawDs.getResult(sql.toString());
		if (sqlResult != null && sqlResult.size() > 0) {
			retTable.setLong(1, "taxCodeId", sqlResult.getLong(1, "taxCodeId"));
			retTable.setDouble(1, "taxRate", sqlResult.getDouble(1, "taxRate"));
		}

		return retTable;
	}

	@Override
	public double getCusDiscount(Long beId, Long cusId) {
		double disc = 0;
		StringBuffer sql = new StringBuffer();
		sql.append("select invoicediscount from cus where id = " + cusId);
		sql.append(" and id != 0");
		sql.append(";");

		SqlTable sqlResult = CawDs.getResult(sql.toString());
		if (sqlResult != null && sqlResult.size() > 0) {
			disc = sqlResult.getDouble(1, "invoicediscount");
		}

		return disc;
	}

	@Override
	public List<SqlTable> calcCusDiscount(Long beId, Long cusId, Long transAccId, Date tDate, String zipcode,
			SqlTable dnt) {
		String[] fields = new String[] { "proId", "unitId", "qty" };
		String xmlStr = (dnt == null || dnt.size() == 0) ? "" : MacXMLUtil.buildXmlString(dnt, fields);
		String m_zipcode = !StringLib.isEmpty(zipcode) && zipcode.length() >= 2 ? zipcode.substring(0, 2) : "";

		StringBuffer sql = new StringBuffer();
		sql.append("call ocf_get_cusdisc(");
		sql.append(beId);
		sql.append(", " + cusId);
		sql.append(", '" + xmlStr + "'");
		sql.append(", " + transAccId);
		sql.append(", '" + DateLib.dateToString(tDate) + "'");
		sql.append(", '" + m_zipcode + "'");
		sql.append(");");

		return CawDs.getResults(sql.toString());
	}

	@Override
	public SqlTable getZipCodeCharge(Long beId, Long transAccId, Date tDate, String zipcode) {
		String m_zipcode = !StringLib.isEmpty(zipcode) && zipcode.length() >= 2 ? zipcode.substring(0, 2) : "";

		StringBuffer sql = new StringBuffer();
		sql.append("call ocf_get_zccharge(");
		sql.append(beId);
		sql.append(", " + transAccId);
		sql.append(", '" + DateLib.dateToString(tDate) + "'");
		sql.append(", '" + m_zipcode + "'");
		sql.append(");");

		return CawDs.getResult(sql.toString());

	}

	@Override
	public SqlTable getDefaultPrintSetting(String providerCode) {

		StringBuilder sb = new StringBuilder();
		sb.append(" select c.code");
		sb.append(" from printcountsetting a");
		sb.append(", printsetting b");
		sb.append(", jrxml c");
		sb.append(" where a.id != 0");
		sb.append(" and a.printSetting = b.id");
		sb.append(" and b.printFormat = c.id");
		sb.append(" and a.module = 'dn'");
		sb.append(" and a.provider = '" + providerCode + "'");
		sb.append(";");

		return CawDs.getResult(sb.toString());
	}

	@Override
	public double getHolidayCharge(long beId, Date tDate) {
		if (DateLib.isEmptyDate(tDate)) {
			return 0d;
		}

		LocalDate locDate = tDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		// Check if it is Sunday
		boolean isSun = locDate.getDayOfWeek().getValue() == 7;
		boolean isPub = false;

		// Check whether the UDF field exists
		boolean udfExist = false;
		DdTable ddTable = CawGlobal.getDDTable("dept");
		Map<String, DdColumn> colMap = ddTable.getColumn();
		if (colMap != null) {
			udfExist = colMap.containsKey("udfSunDeliveryCharge") && colMap.containsKey("udfPHDeliveryCharge");
		}

		if (!udfExist) {
			CawLog.info("Does not have udfSunDeliveryCharge & udfPHDeliveryCharge.");
			return 0d;
		}

		DdTable ddTable2 = CawGlobal.getDDTable("udfphdate");
		if (ddTable2 == null) {
			CawLog.info("Does not have table udfphdate.");
			return 0d;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(" select id, udfSunDeliveryCharge, udfPHDeliveryCharge");
		sb.append(" from dept where id != 0 and id = " + beId);
		sb.append(";");

		sb.append(" select * from udfphdate where hId = " + beId);
		sb.append(";");

		List<SqlTable> tab_results = CawDs.getResults(sb.toString());

		SqlTable dept = tab_results.get(0);
		SqlTable udfphdate = tab_results.get(1);

		if (!isSun) {
			if (udfphdate != null && udfphdate.size() > 0) {
				for (int i : udfphdate) {
					Date m_date = (Date) udfphdate.getObject(i, "udfPHDate");
					if (m_date != null && m_date.compareTo(tDate) == 0) {
						isPub = true;
						break;
					}
				}
			}
		}

		if (isSun) {
			if (dept != null && dept.size() > 0) {
				return dept.getDouble(1, "udfSunDeliveryCharge");
			}
		} else if (isPub) {
			if (dept != null && dept.size() > 0) {
				return dept.getDouble(1, "udfPHDeliveryCharge");
			}
		}

		return 0d;
	}
}
