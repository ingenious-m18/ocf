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

		// Show at most 5 product footers
		checkProFooterLimit(reData, 5);

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

			// Trim left & right
			String str_fieldValue = m_fieldValue.toString().trim();

			// Count 40 chars per row
			int c_wordCnt = 0;
			int c_rowCnt = 1;
			int c_ttlWordCnt = 0;
			int length = str_fieldValue.length();
			StringBuilder sb = new StringBuilder();

			// Original way to split content into rows
			// while (true) {
			// if (c_ttlWordCnt == length) {
			// break;
			// }
			//
			// if (c_rowCnt > rowLimit) {
			// break;
			// }
			//
			// if (c_wordCnt < wordLimit) {
			// sb.append(str_fieldValue.substring(c_ttlWordCnt, c_ttlWordCnt + 1));
			//
			// c_wordCnt++;
			// } else {
			//
			// c_wordCnt = 0;
			// c_rowCnt++;
			//
			// if (c_rowCnt > rowLimit) {
			// break;
			// }
			//
			// sb.append("\n");
			// sb.append(str_fieldValue.substring(c_ttlWordCnt, c_ttlWordCnt + 1));
			//
			// c_wordCnt++;
			// }
			//
			// c_ttlWordCnt++;
			// }

			// Split the str by space
			String[] wordArray = str_fieldValue.split(" ");
			boolean first = true;
			if (wordArray != null) {
				WORD_LOOP: for (String word : wordArray) {

					int w_len = word.length();

					// Special case if w_len already > wordLimit
					if (w_len > wordLimit) {
						List<String> brkList = breakDownLongWord(word, wordLimit, rowLimit);
						for (String brkWd : brkList) {

							int brk_len = brkWd.length();
							if (first) {
								if (brk_len <= wordLimit) { // Append the word directly
									sb.append(brkWd);
									c_wordCnt += brk_len;

								} else { // Go to next line
									if (c_rowCnt >= rowLimit) {
										sb.append("\n");
										sb.append("......as per card.");
										break WORD_LOOP;
									}

									sb.append("\n").append(brkWd);
									c_rowCnt++;
									c_wordCnt = brk_len;
								}

								first = false;
							} else {

								if (c_wordCnt + brk_len + 1 <= wordLimit) { // Append the word directly
									sb.append(" ").append(brkWd);
									c_wordCnt += (brk_len + 1);
								} else { // Go to next line
									if (c_rowCnt >= rowLimit) {
										sb.append("\n");
										sb.append("......as per card.");
										break WORD_LOOP;
									}

									sb.append("\n").append(brkWd);
									c_rowCnt++;
									c_wordCnt = brk_len;
								}
							}
						}

						// continue next word
						continue;
					}

					if (first) {
						if (c_wordCnt + w_len <= wordLimit) { // Append the word directly
							sb.append(word);
							c_wordCnt += w_len;
						} else { // Go to next line
							if (c_rowCnt >= rowLimit) {
								sb.append("\n");
								sb.append("......as per card.");
								break WORD_LOOP;
							}

							sb.append("\n").append(word);
							c_rowCnt++;
							c_wordCnt = w_len;
						}

						first = false;

					} else {
						if (c_wordCnt + w_len + 1 <= wordLimit) { // Append the word directly
							sb.append(" ").append(word);
							c_wordCnt += (w_len + 1);

						} else { // Go to next line
							if (c_rowCnt >= rowLimit) {
								sb.append("\n");
								sb.append("......as per card.");
								break WORD_LOOP;
							}

							sb.append("\n").append(word);
							c_rowCnt++;
							c_wordCnt = w_len;
						}

					}
				}
			}

			table.setString(row, fieldName, sb.toString());
		}
	}

	// If the word itself is longer than the word limit
	// Break it into parts, each part max size = wordLimit
	private List<String> breakDownLongWord(String word, int wordLimit, int rowLimit) {
		List<String> wdList = ListLib.newList();

		String temp_word = word;
		while (temp_word.length() > wordLimit) {
			String trunc = temp_word.substring(0, wordLimit);
			wdList.add(trunc);
			temp_word = temp_word.substring(wordLimit);
		}

		if (temp_word.length() > 0) {
			wdList.add(temp_word);
		}

		return wdList;
	}

	private void checkProFooterLimit(CawReportDataSet reData, int proFooterLimit) {
		CawReportSqlTable maindn = reData.getSqlResult("maindn");
		CawReportSqlTable dnt = reData.getSqlResult("dnt");

		maindn.addField(new SqlTableField("overProFooterLimit", Boolean.class));
		dnt.addField(new SqlTableField("prtProFooter", Boolean.class));

		for (int i : maindn) {
			long dnId = maindn.getLong(i, "dnId");

			int counter = 0;
			for (int j : dnt) {
				if (dnt.getLong(j, "dnId") == dnId) {
					if (++counter > proFooterLimit) {
						dnt.setBoolean(j, "prtProFooter", false);
					} else {
						dnt.setBoolean(j, "prtProFooter", true);
					}
				}
			}

			if (counter > proFooterLimit) {
				maindn.setBoolean(i, "overProFooterLimit", true);
			} else {
				maindn.setBoolean(i, "overProFooterLimit", false);
			}
		}
	}
}
