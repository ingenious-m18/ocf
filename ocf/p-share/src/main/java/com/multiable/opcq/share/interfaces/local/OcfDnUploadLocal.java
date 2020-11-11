package com.multiable.opcq.share.interfaces.local;

import java.io.File;

import com.multiable.core.share.data.SqlTable;

public interface OcfDnUploadLocal {

	public SqlTable readDoc(Long fileId);

	public File getOcrPackage();
}
