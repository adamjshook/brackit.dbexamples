/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.dbexamples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.Random;

import org.brackit.driver.BrackitConnection;

/**
 * Basic usage scenarios.
 */
public class Simple {

	enum Severity {
		low, high, critical
	};

	public static void main(String[] args) {
		try {
			singleQuery();
			catalog();
			documentHandling();
			txManagement();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static BrackitConnection getConnection() throws Exception {
		return new BrackitConnection("localhost", 11011);
	}

	private static void singleQuery() throws Exception {
		BrackitConnection con = getConnection();
		try {
			// run a simple query
			con.query("1+1", System.out);
			System.out.println();
		} finally {
			con.close();
		}
	}

	private static void catalog() throws Exception {
		BrackitConnection con = getConnection();
		try {
			// display the whole catalog
			con.query("doc('_master.xml')", System.out);

			// query the catalog to get a list of all collections in the DB
			System.out.println();
			con.query("doc('_master.xml')//collection/@name/string()",
					System.out);
		} finally {
			con.close();
		}
	}

	private static void documentHandling() throws Exception {
		// prepare directory with sample documents
		File tmpDir = new File(System.getProperty("java.io.tmpdir"));
		File dir = new File(tmpDir + File.separator + "docs"
				+ System.currentTimeMillis());
		if (!dir.mkdir()) {
			throw new IOException("Directory " + dir + " already exists");
		}
		dir.deleteOnExit();
		for (int i = 0; i < 10; i++) {
			generateSampleDoc(dir, "sample", i);
		}

		BrackitConnection con = getConnection();
		try {
			// import documents in database
			con.query(String.format(
					"bit:load('mydocs.col', io:ls('%s', '\\.xml$'))", dir),
					System.out);

			// show the catalog entry of the collection
			System.out.println();
			con.query("doc('_master.xml')//collection[@name = '/mydocs.col']",
					System.out);

			// fetch 1. document from database
			System.out.println();
			con.query("fn:collection('/mydocs.col')[1]", System.out);

			// create an CAS typed index for the element path '//src'
			System.out.println();
			con.query(
					"bdb:create-cas-index('/mydocs.col', 'xs:string', '//src')",
					System.out);

			// show the catalog entry of the collection
			System.out.println();
			con.query("doc('_master.xml')//collection[@name = '/mydocs.col']",
					System.out);

			// created CAS index for the IP address range
			// 192.168.0.0 <= ? <= 192.168.128.128
			// (assuming that the created CAS index hast the ID 8)
			System.out.println();
			con.query(
					"bdb:scan-cas-index('/mydocs.col', 8, "
							+ "'192.168.0.0', '192.168.128.128', xs:boolean(1), xs:boolean(1), ())",
					System.out);
			System.out.println();
		} finally {
			con.close();
		}
	}

	private static void txManagement() throws Exception {
		BrackitConnection con = getConnection();
		try {
			// begin a new transaction
			// (disable auto-commit for the following statements)
			con.begin();

			// store a sample document
			con.query("bit:store('sample.xml', <foo><bar/></foo>)", System.out);

			// commit the changes
			con.commit();

			// begin a another transaction
			con.begin();

			// display the sample document
			con.query("doc('/sample.xml')", System.out);

			// update the sample document
			System.out.println();
			con.query("insert node <test/> into doc('/sample.xml')/foo/bar",
					System.out);

			// display the updated sample document
			con.query("doc('/sample.xml')", System.out);

			// rollback the changes
			con.rollback();

			// display the sample document after rollback
			System.out.println();
			con.query("doc('/sample.xml')", System.out);
		} finally {
			con.close();
		}
	}

	private static File generateSampleDoc(File dir, String prefix, int no)
			throws IOException {
		File file = File.createTempFile("sample", ".xml", dir);
		file.deleteOnExit();
		PrintStream out = new PrintStream(new FileOutputStream(file));
		Random rnd = new Random();
		long now = System.currentTimeMillis();
		int diff = rnd.nextInt(6000 * 60 * 24 * 7);
		Date tst = new Date(now - diff);
		Severity sev = Severity.values()[rnd.nextInt(3)];
		String src = "192.168." + (1 + rnd.nextInt(254)) + "."
				+ (1 + rnd.nextInt(254));
		int mlen = 10 + rnd.nextInt(70);
		byte[] bytes = new byte[mlen];
		int i = 0;
		while (i < mlen) {
			int wlen = 1 + rnd.nextInt(8);
			int j = i;
			while (j < Math.min(i + wlen, mlen)) {
				bytes[j++] = (byte) ('a' + rnd.nextInt('z' - 'a' + 1));
			}
			i = j;
			if (i < mlen - 1) {
				bytes[i++] = ' ';
			}
		}
		String msg = new String(bytes);
		out.print("<?xml version='1.0'?>");
		out.print(String.format("<log tstamp='%s' severity='%s'>", tst, sev));
		out.print(String.format("<src>%s</src>", src));
		out.print(String.format("<msg>%s</msg>", msg));
		out.print("</log>");
		out.close();
		return file;
	}
}