package com.multiable.erp.opcq.ireport.provider.dn;

import java.util.List;

import com.multiable.core.ejb.ds.MacQuery;
import com.multiable.core.share.data.ireport.CawReportDataSet;
import com.multiable.core.share.data.ireport.CawReportSqlTable;
import com.multiable.core.share.lib.ListLib;
import com.multiable.core.share.lib.StringLib;
import com.multiable.core.share.util.ireport.CawJrVar;
import com.multiable.erp.core.ejb.ireport.MacModuleProvider;
import com.multiable.erp.core.share.util.MacJrVar;
import com.multiable.erp.core.share.util.MacReportUtil;
import com.multiable.erp.trdg.share.data.ireport.dn.DnJrDto;
import com.multiable.erp.trdg.share.util.TradeJrVar;

public class OcfDeliveryNoteProvider extends MacModuleProvider {
	@Override
	public void initReportStru(CawReportDataSet reData) {
		reData.setQuery(0);
		int i = -1;
		i = MacReportUtil.setAlias(reData, i, "maindn", "maindn");
		i = MacReportUtil.setAlias(reData, i, "dnt", "dnt");
		i = MacReportUtil.setAlias(reData, i, "remdn", "remdn");
		i = MacReportUtil.setAlias(reData, i, "staff", "staff");
	}

	@Override
	public void adjustData(CawReportDataSet reData) {
		super.adjustData(reData);
		DnJrDto jrDto = (DnJrDto) getReportDto();
		// handleHtmlField(reData, jrDto);

		reData.setRelationTo("maindn", "dnId", "remdn");
		reData.setRelationTo("maindn", "staffId", "id", "staff");

		reData.assignSubReport("maindn", "dnt", new String[] {}, "dnId");

		// Turn HTML field to Text field
		CawReportSqlTable remdn = reData.getSqlResult("remdn");
		handleHtmlField(remdn, new String[] { "ocfsender", "msgcontent", "ocfrecipient" }, false);

		// Check word limit and show '......as per card.'
		checkWordLimit(remdn, 1, 200, 5, "ocfsender");
		checkWordLimit(remdn, 1, 200, 5, "msgcontent");
		checkWordLimit(remdn, 1, 80, 2, "ocfrecipient");
	}

	@Override
	public CawReportDataSet genIdsData() {
		super.genIdsData();

		DnJrDto jrDto = (DnJrDto) getReportDto();
		String sql = "{CALL ocf_prtDn('" + jrDto.getMainIdString() + "')}";
		MacQuery query = new MacQuery();
		query.setQuery(sql);
		return fillAndAdjustData(query);
	}

	@Override
	public MacJrVar getJrVar(CawJrVar jrVar) {
		return new TradeJrVar(jrVar);
	}

	private void checkWordLimit(CawReportSqlTable table, int row, int wordLimit, int rowLimit, String fieldName) {
		String fieldValue = table.getString(row, fieldName);

		if (StringLib.isNotEmpty(fieldValue)) {
			List<String> splitList = ListLib.newList();
			StringLib.split(splitList, fieldValue, "\n");

			int count = 0;
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < splitList.size(); i++) {

				String cur_str = splitList.get(i);
				cur_str = cur_str.trim();

				count++;
				if (count > rowLimit) {
					break;
				}
				int cur_len = cur_str.length();
				if (cur_len > wordLimit) {
					sb.append(StringLib.subString(cur_str, 0, wordLimit));
				} else {
					sb.append(cur_str);
				}

				if (i != splitList.size() - 1) {
					sb.append("\n");
				}
			}

			if (count > rowLimit) {
				sb.append("......as per card.");
			}

			table.setString(row, fieldName, sb.toString());

			// int cur_len = fieldValue.length();
			// if (cur_len > wordLimit) {
			// String adj_fieldValue = StringLib.subString(fieldValue, 0, wordLimit);
			// adj_fieldValue = adj_fieldValue + "/n" + "......as per card.";
			// table.setString(row, fieldName, adj_fieldValue);
			// }
		}
	}
}
