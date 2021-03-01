package com.multiable.erp.ocf.ireport.provider.dn;

import java.util.List;

import com.multiable.core.ejb.ds.MacQuery;
import com.multiable.core.share.data.SqlTableField;
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
		checkWordLimit(remdn, 1, 40, 5, "ocfsender");
		checkWordLimit(remdn, 1, 40, 5, "msgcontent");
		checkWordLimit(remdn, 1, 40, 3, "ocfrecipient");

		// UDF Field Handling
		CawReportSqlTable maindn = reData.getSqlResult("maindn");
		if (!maindn.isFieldExist("udfRemark")) {
			maindn.addField(new SqlTableField("udfRemark", String.class));
		}
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

		// if (StringLib.isNotEmpty(fieldValue)) {
		// List<String> splitList = ListLib.newList();
		// StringLib.split(splitList, fieldValue, "\n");
		//
		// int count = 0;
		// StringBuilder sb = new StringBuilder();
		//
		// for (int i = 0; i < splitList.size(); i++) {
		//
		// String cur_str = splitList.get(i);
		// cur_str = cur_str.trim();
		//
		// count++;
		// if (count > rowLimit) {
		// break;
		// }
		// int cur_len = cur_str.length();
		// if (cur_len > wordLimit) {
		// sb.append(StringLib.subString(cur_str, 0, wordLimit));
		// } else {
		// sb.append(cur_str);
		// }
		//
		// if (i != splitList.size() - 1) {
		// sb.append("\n");
		// }
		// }
		//
		// if (count > rowLimit) {
		// sb.append("......as per card.");
		// }
		//
		// table.setString(row, fieldName, sb.toString());
		//
		// }

		// Use another way to insert ......as per card.
		if (StringLib.isNotEmpty(fieldValue)) {
			// // Ignore the first space
			// if (fieldValue.substring(0, 1).equals(" ")) {
			// fieldValue = fieldValue.substring(1);
			// }

			// // Replace all "\n"
			// fieldValue = fieldValue.replaceAll("\n", " ");

			List<String> splitList = ListLib.newList();
			StringLib.split(splitList, fieldValue, "\n");
			StringBuilder m_fieldValue = new StringBuilder();
			for (int i = 0; i < splitList.size(); i++) {
				String c_str = splitList.get(i);
				m_fieldValue.append(c_str.trim());

				if (i != splitList.size() - 1) {
					m_fieldValue.append(" ");
				}
			}

			// Count 40 chars per row
			int c_wordCnt = 0;
			int c_rowCnt = 1;
			int c_ttlWordCnt = 0;
			int length = m_fieldValue.length();
			StringBuilder sb = new StringBuilder();

			while (true) {
				if (c_ttlWordCnt == length) {
					break;
				}

				if (c_rowCnt > rowLimit) {
					break;
				}

				if (c_wordCnt < wordLimit) {
					sb.append(m_fieldValue.substring(c_ttlWordCnt, c_ttlWordCnt + 1));

					c_wordCnt++;
				} else {
					c_wordCnt = 0;
					c_rowCnt++;

					if (c_rowCnt > rowLimit) {
						break;
					}

					sb.append("\n");
					sb.append(m_fieldValue.substring(c_ttlWordCnt, c_ttlWordCnt + 1));

					c_wordCnt++;
				}

				c_ttlWordCnt++;

			}

			if (c_rowCnt > rowLimit) {
				sb.append("\n");
				sb.append("......as per card.");
			}

			table.setString(row, fieldName, sb.toString());
		}
	}
}
