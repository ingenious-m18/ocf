package com.multiable.erp.opcq.eao.bean;

import java.util.Date;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.data.SqlTableField;
import com.multiable.core.share.lib.DateLib;
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.lib.StringLib;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OpcqDNLocal;

@Stateless(name = OcfEJB.OpcqDNEJB)
@Local({ OpcqDNLocal.class })
public class OpcqDNEJB implements OpcqDNLocal {
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
}
