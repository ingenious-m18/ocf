package com.multiable.erp.opcq.ireport.provider.siso;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.ejb.ds.MacQuery;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.data.SqlTableField;
import com.multiable.core.share.data.ireport.CawReportDataSet;
import com.multiable.core.share.data.ireport.CawReportSqlTable;
import com.multiable.core.share.util.ireport.CawJrVar;
import com.multiable.erp.core.ejb.ireport.MacModuleProvider;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.erp.core.share.util.MacJrVar;
import com.multiable.erp.core.share.util.MacReportUtil;
import com.multiable.erp.trdg.share.data.ireport.siso.SisoJrDto;
import com.multiable.erp.trdg.share.util.TradeJrVar;

public class OcfSalesInvoiceProvider extends MacModuleProvider {
	@Override
	public void initReportStru(CawReportDataSet reData) {
		reData.setQuery(0);
		int i = -1;
		i = MacReportUtil.setAlias(reData, i, "mainsi", "maintar");
		i = MacReportUtil.setAlias(reData, i, "sit", "art");
		i = MacReportUtil.setAlias(reData, i, "sidisc", "sidisc");
		i = MacReportUtil.setAlias(reData, i, "remsi", "remtar");
		i = MacReportUtil.setAlias(reData, i, "cli", "cus");
		i = MacReportUtil.setAlias(reData, i, "ocfremcust", "ocfremcust");

	}

	@Override
	public void adjustData(CawReportDataSet reData) {
		super.adjustData(reData);
		SisoJrDto jrDto = (SisoJrDto) getReportDto();

		reData.setRelationTo("mainsi", "siId", "remsi");
		reData.setRelationTo("mainsi", "cusId", "id", "cli");
		reData.setRelationTo("cli", "id", "hId", "ocfremcust");

		reData.assignSubReport("mainsi", "sit", new String[] {}, "siId");

		// Turn HTML field to Text field
		CawReportSqlTable remsi = reData.getSqlResult("remsi");
		handleHtmlField(remsi, new String[] { "remarks" }, false);

		// Calculate redeemed credit
		calcRedempCredit(reData);

		// Calculate redemption coupon (Point earned for this invoice)
		calcRedempCoupon(reData);
	}

	@Override
	public CawReportDataSet genIdsData() {
		super.genIdsData();

		SisoJrDto jrDto = (SisoJrDto) getReportDto();
		String sql = "{CALL ocf_prtSiso('" + jrDto.getMainIdString() + "')}";
		MacQuery query = new MacQuery();
		query.setQuery(sql);
		return fillAndAdjustData(query);
	}

	private void calcRedempCredit(CawReportDataSet reData) {
		CawReportSqlTable mainsi = reData.getSqlResult("mainsi");
		CawReportSqlTable sidisc = reData.getSqlResult("sidisc");

		mainsi.addField(new SqlTableField("redeemCredit", Double.class));

		// Step: Get redemption credit account
		String sql = "select id from chacc where redemptioncreditaccount = 1";
		SqlTable redResult = CawDs.getResult(sql);
		if (redResult != null && redResult.size() > 0) {
			// Set index
			TableStaticIndexAdapter redIndex = new TableStaticIndexAdapter(redResult) {
				@Override
				public String getIndexKey() {
					return src.getValueStr(srcRow, "id");
				}
			};
			redIndex.action();

			for (int i : mainsi) {
				double redeemCredit = 0d;

				for (int j : sidisc) {
					if (sidisc.getLong(j, "hId") == mainsi.getLong(i, "id")) {
						if (redIndex.seek("" + sidisc.getLong(j, "accId")) > 0) {
							redeemCredit += sidisc.getDouble(j, "amt");
						}
					}
				}

				mainsi.setValue(i, "redeemCredit", redeemCredit);
			}

		}

	}

	private void calcRedempCoupon(CawReportDataSet reData) {
		CawReportSqlTable mainsi = reData.getSqlResult("mainsi");
		CawReportSqlTable ocfremcust = reData.getSqlResult("ocfremcust");

		mainsi.addField(new SqlTableField("redeemCoupon", Double.class));

		for (int i : mainsi) {
			double redeemCoupon = 0d;
			for (int j : ocfremcust) {
				if (ocfremcust.getLong(j, "hId") == mainsi.getLong(i, "cusId")) {

					redeemCoupon += ocfremcust.getDouble(j, "pointearned");

				}
			}
			mainsi.setValue(i, "redeemCoupon", redeemCoupon);
		}
	}

	@Override
	public MacJrVar getJrVar(CawJrVar jrVar) {
		return new TradeJrVar(jrVar);
	}

}
