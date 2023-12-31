package github.hisuzume.httpd;

import android.app.appsearch.ReportSystemUsageRequest;
import fi.iki.elonen.NanoHTTPD;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.Executors;

public class Httpd extends NanoHTTPD {
	private String rootDir;
	private boolean enableFileList = false;

	/* 例如你访问 http://localhost:8080/ ，那么你可能实际访问 http://localhost:8080/index.html */
	public static final String[] DICTIONARY_DEFAULT_FILES = new String[] { "index.html", "index.htm", };

	public Httpd(int p, String rootDir) {
		super(p);
		this.rootDir = rootDir;
	}

	// 允许文件列表
	public void setFileListEnable(boolean shit) {
		enableFileList = shit;
	}

	public static String getMimeForFile(File f) {
		return getMimeForFile(f + "");
	}

	public static String getMimeForFile(String f) {
		// 默认：输出
		String mime = "application/octet-stream";
		try {
			// 获取后辍名
			switch (f.substring(f.lastIndexOf(".") + 1)) {
			case "css":
				mime = "text/css";
				break;
			case "htm":
			case "html":
				mime = "text/html";
				break;
			case "xml":
				mime = "text/xml";
				break;
			case "md":
			case "txt":
				mime = "text/plain";
				break;
			case "gif":
				mime = "image/gif";
				break;
			case "jpg":
			case "jpeg":
				mime = "image/jpeg";
				break;
			case "png":
				mime = "image/png";
				break;
			case "svg":
				mime = "image/svg+xml";
				break;
			case "mp3":
				mime = "audio/mpeg";
				break;
			case "m3u":
				mime = "audio/mpeg-url";
				break;
			case "mp4":
				mime = "video/mp4";
				break;
			case "flv":
				mime = "video/x-flv";
				break;
			case "js":
				mime = "application/javascript";
				break;
			case "ogg":
				mime = "application/x-ogg";
				break;
			case "m3u8":
				mime = "application/vnd.apple.mpegurl";
				break;
			case "ts":
				mime = "video/mp2t";
				break;
			}
		} catch (Exception e) {
			// 忽略...
		}
		return mime;
	}

	@Override
	public NanoHTTPD.Response serve(final NanoHTTPD.IHTTPSession req) {
		
		try {
			File reqFile = new File(rootDir + req.getUri());

			// 检测文件是否存在
			if (!reqFile.exists())
				return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 - 你所请求的资源不存在！");

			if (reqFile.isDirectory()) {
				// 检测诸如 index.html 的默认访问文件
				File _reqFile;
				for (String defFile : DICTIONARY_DEFAULT_FILES) {
					_reqFile = new File(reqFile + "/" + defFile);
					if (_reqFile.exists())
						return newChunkedResponse(Response.Status.OK, null, new FileInputStream(_reqFile));
				}

				// 访问文件夹处理	
				if (enableFileList) {
					String inside = "<a href='../'>../</a><br/>";
					try {
						for (File subFile : reqFile.listFiles()) {
							if (subFile.isFile())
								inside += "<a href='" + subFile.getName() + "'>" + subFile.getName() + "</a><br/>";
							else
								inside += "<a href='" + subFile.getName() + "/'>" + subFile.getName() + "/</a><br/>";
						}
					} catch (Exception e) {
						// 忽略...
					}
					// 返回文件列表
					return newFixedLengthResponse(Response.Status.OK, "text/html",
							"<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><meta charset='utf-8'><title>文件列表 - "
									+ reqFile + "</title></head><body><h>文件列表：</h><br/>" + inside + "</body></html>");
				} else
					//报告错误信息
					return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "400 - 无效访问！");
			}

			String range = req.getHeaders().get("Range");
			if (range == null)
				range = req.getHeaders().get("range");

			if (range != null) {
				// 断点续传 常见于视频、音频、继续下载等

				// 此处代码由夏小沫的AI小站辅助编写 感谢夏沫

				FileInputStream reqFileInput = new FileInputStream(reqFile);

				// 计算文件读取的起始点以及长度
				long fileLength = reqFile.length();
				long startRange = 0;
				long endRange = fileLength - 1;

				String[] rangeValues = range.split("=")[1].split("-");
				if (rangeValues.length > 1) {
					startRange = Long.parseLong(rangeValues[0]);
					endRange = Long.parseLong(rangeValues[1]);
				} else {
					startRange = Long.parseLong(rangeValues[0]);
				}

				long contentLength = endRange - startRange + 1;

				// 跳过部分读取
				reqFileInput.skip(startRange);

				Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, getMimeForFile(reqFile),
						reqFileInput, contentLength);
				response.addHeader("Content-Length", String.valueOf(contentLength));
				response.addHeader("Content-Range", "bytes " + startRange + "-" + endRange + "/" + fileLength);
				response.addHeader("Accept-Ranges", "bytes");

				return response;
			} else {
				// 默认文件处理
				Response res = newFixedLengthResponse(Response.Status.OK, getMimeForFile(reqFile),
						new FileInputStream(reqFile), reqFile.length());
				res.addHeader("Content-Length", String.valueOf(reqFile.length()));
				res.addHeader("Accept-Ranges", "bytes");
				return res;
			}
		} catch (Exception e) {
			// 异常处理
			return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain",
					"500 - 服务器出错！" + e.getMessage());
		}
	}

}
