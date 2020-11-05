package com.multiable.erp.opcq.eao.bean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import javax.naming.NamingException;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import com.multiable.CawContext;
import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.ejb.eao.curd.SeReadParam;
import com.multiable.core.ejb.eao.curd.SeSaveParam;
import com.multiable.core.ejb.eao.curd.SqlTableCurdEAO;
import com.multiable.core.ejb.eao.file.FileCurdEao;
import com.multiable.core.ejb.eao.localinterface.SqlEntityEAOLocal;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.data.SqlTableField;
import com.multiable.core.share.dto.ge.FileInfoDto;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.lib.CawLib;
import com.multiable.core.share.lib.DateFormatLib;
import com.multiable.core.share.lib.DateLib;
import com.multiable.core.share.lib.FileLib;
import com.multiable.core.share.lib.ListLib;
import com.multiable.core.share.lib.StringLib;
import com.multiable.core.share.localinterface.general.FileLocal;
import com.multiable.core.share.server.CawGlobal;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.logging.CawLog;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OcfDnUploadLocal;

import net.sourceforge.tess4j.Tesseract;

@Stateless(name = OcfEJB.OcfDnUploadEJB)
@Local({ OcfDnUploadLocal.class })
public class OcfDnUploadEJB implements OcfDnUploadLocal {
	@EJB
	private FileLocal fileEJB;
	@EJB
	private FileCurdEao fileEao;
	@EJB
	private SqlTableCurdEAO sqlTableCurdEao;

	@Override
	public SqlTable readDoc(Long fileId) {
		SqlTable retTable = new SqlTable();
		retTable.addField(new SqlTableField("code", String.class));
		retTable.addField(new SqlTableField("result", String.class));
		retTable.addField(new SqlTableField("remarks", String.class));

		try {

			Tesseract tesseract = new Tesseract();
			// Tesseract tesseract = Tesseract.getInstance();

			CawLog.info("Initialize Tesseract");

			FileInfoDto fileInfo = fileEJB.getFileInfoDto(fileId);

			ClassLoader loader = getClass().getClassLoader();
			InputStream inStream = loader.getResourceAsStream("/tessdata/eng.traineddata");

			String path = CawLib.getPath_Jboss() + File.separator + "ocfInput";
			String tessDataDir = path + File.separator + "tessdata";
			Path tessDataPath = Paths.get(tessDataDir);
			if (!Files.exists(tessDataPath, LinkOption.NOFOLLOW_LINKS)) {
				FileLib.createFolder(tessDataDir);
			}

			CawLog.info("Check ocfInput folder exists");

			// Copy traineddata to specific dir
			Path trainDataPath = Paths.get(tessDataDir + File.separator + "eng.traineddata");
			boolean exist = Files.exists(trainDataPath, LinkOption.NOFOLLOW_LINKS);

			if (!exist && inStream != null) {
				try {
					Files.copy(inStream, trainDataPath, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			CawLog.info("Copy traineddata");

			if (fileInfo != null) {
				try {

					// the font library
					tesseract.setDatapath(path);
					tesseract.setLanguage("eng");

					CawLog.info("Set font library for tesseract");

					// Split PDF
					// Loading an existing PDF document
					// String pdfPath = path + File.separator + "DN_Scan02.pdf";
					// File pdfFile = new File(pdfPath);

					String pdfPath = fileEao.getFilePath(fileId);
					File pdfFile = new File(pdfPath);
					PDDocument pdfDoc = PDDocument.load(pdfFile);

					// Instantiating Splitter class
					Splitter splitter = new Splitter();

					// Splitting the pages of a PDF document
					List<PDDocument> pages = splitter.split(pdfDoc);

					// Create an iterator
					Iterator<PDDocument> iterator = pages.iterator();

					// Handling each page with OCR
					int i = 1;
					Map<String, List<Integer>> checkMap = new HashMap<String, List<Integer>>();
					while (iterator.hasNext()) {
						PDDocument m_doc = iterator.next();

						// Write the object into a file
						File tempFile = new File(path + File.separator + "file" + i + ".pdf");
						FileOutputStream fOut = new FileOutputStream(tempFile);
						m_doc.save(fOut);

						// Do OCR
						ImageIO.scanForPlugins();
						String text = tesseract.doOCR(new File(path + File.separator + "file" + i + ".pdf"));

						CawLog.info("Read text from split PDF " + i);
						// String text = tesseract.doOCR(new File(path + File.separator + "scanTest4.jpg"));

						Pattern p = Pattern.compile("Document\\s*:\\s*#*(\\S+)#*\\s+");
						Matcher m = p.matcher(text);

						if (m.find()) {

							String docNo = m.group(1);

							if (StringLib.isNotEmpty(docNo)) {
								if (checkMap.containsKey(docNo)) {
									checkMap.get(docNo).add(i);
								} else {
									List<Integer> intList = new ArrayList<Integer>();
									intList.add(i);
									checkMap.put(docNo, intList);
								}
							}
						}

						i++;

					}

					// init using JNDI
					SqlEntityEAOLocal entityEao = null;
					try {
						entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
					} catch (NamingException e) {
						e.printStackTrace();
					}

					List<String> failRecs = ListLib.newList();
					for (String key : checkMap.keySet()) {
						// Check if DN exists
						String sql = "select id from maindn where code = '" + key + "';";
						SqlTable dnResult = CawDs.getResult(sql);
						if (dnResult != null && dnResult.size() > 0) {
							List<Integer> tempList = checkMap.get(key);
							if (ListLib.isNotEmpty(tempList)) {
								// Set file name
								Date uploadTime = DateLib.getCurTime();
								String fileName = key + DateFormatLib.date2Str(uploadTime, "yyyyMMddHHmmss");

								// Instantiating PDFMergerUtility class
								PDFMergerUtility PDFmerger = new PDFMergerUtility();

								// Setting the destination file
								PDFmerger.setDestinationFileName(path + File.separator + fileName + ".pdf");

								for (Integer ir : tempList) {
									PDFmerger.addSource(path + File.separator + "file" + ir + ".pdf");
								}

								// Merge document
								PDFmerger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());

								CawLog.info("PDF Merge");

								// Attach to DN
								SeReadParam readParam = new SeReadParam("dn");
								readParam.setEntityId(dnResult.getLong(1, "id"));
								SqlEntity dnEntity = entityEao.loadEntity(readParam);

								SqlTable maindn = dnEntity.getMainData();
								SqlTable maindn_attach = dnEntity.getData("maindn_attach");

								// Save file to get merged pdf fileId
								Long m_fileId = fileEao.saveFile(".pdf",
										path + File.separator + fileName + ".pdf", fileName);
								FileInfoDto info = fileEao.getFileInfo(m_fileId);
								if (info != null) {
									maindn.setValue(1, "lastUploadTime", DateLib.dateTimeToString(uploadTime));
									maindn.setValue(1, "sendViaEmail", true);
									maindn.setValue(1, "iRev", maindn.getInteger(1, "iRev") + 1);

									int rec = maindn_attach.addRow();
									maindn_attach.setValue(rec, "iRev", 1);
									maindn_attach.setValue(rec, "hId", dnResult.getLong(1, "id"));
									maindn_attach.setValue(rec, "filedataId", m_fileId);
									maindn_attach.setValue(rec, "code", fileName + ".pdf");
									maindn_attach.setValue(rec, "desc", fileName);
									maindn_attach.setValue(rec, "fileSize", info.getSize());
									maindn_attach
											.setValue(rec, "createUid",
													(CawContext.getUser() == null
															? CawGlobal.getSysUser().getUid()
															: CawContext.getUser().getUid()));
									maindn_attach.setValue(rec, "createDate", uploadTime);
									maindn_attach.setValue(rec, "author",
											(CawContext.getUser() == null
													? CawGlobal.getSysUser().getUsercode()
													: CawContext.getUser().getUsercode()));
									maindn_attach.setValue(rec, "tags", "Upload via [Delivery Note Upload]");

									SeSaveParam saveParam = new SeSaveParam("dn");
									saveParam.setSqlEntity(dnEntity);
									entityEao.saveEntity(saveParam);

									CawLog.info("Save success: " + key);

									int retRow = retTable.addRow();
									retTable.setValue(retRow, "code", key);
									retTable.setValue(retRow, "result", "Y");

								}
							}
						} else {

							// Record the failed DN codes
							failRecs.add(key);

							int retRow = retTable.addRow();
							retTable.setValue(retRow, "code", key);
							retTable.setValue(retRow, "result", "N");
							retTable.setValue(retRow, "remarks",
									"Cannot find corresponding Delivery Note records");
						}

					}

					// the path of your tess data folder
					// inside the extracted file

					// String text = tesseract.doOCR(new File(path + File.separator + "DN_Scan01.pdf"));

					// path of your image file
					// System.out.println(text);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			int retRow = retTable.addRow();
			retTable.setValue(retRow, "code", "");
			retTable.setValue(retRow, "result", "N");
			retTable.setValue(retRow, "remarks", e.toString());

		}

		return retTable;

	}

}
