/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.jboss.bqt.client;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jboss.bqt.client.api.QueryScenario;
import org.jboss.bqt.framework.TestResult;

public class TestResultsSummary {

	private static final String OVERALL_SUMMARY_FILE = "Summary_totals.txt";
	private static final String OVERALL_SUMMARY_ERROR_FILE = "Summary_errors.txt";
	private static final String CONNECTION_EXCEPTION_SUMMARY_ERROR_FILE = "Summary_connection_exception_errors.txt";
	private static final SimpleDateFormat FILE_NAME_DATE_FORMATER = new SimpleDateFormat(
			"yyyyMMdd_HHmmss"); //$NON-NLS-1$

//	private static final String NL = System.getProperty("line.separator"); //$NON-NLS-1$

	// totals for scenario
	private int total_queries = 0;
	private int total_pass = 0;
	private int total_fail = 0;
	private int total_querysets = 0;
	private long total_seconds = 0;
	private List<String> failed_queries = new ArrayList<String>();
	private List<String> query_sets = new ArrayList<String>(10);
	private QueryScenario scenario = null;

	private Map<String, Collection<TestResult>> TestResults = Collections
			.synchronizedMap(new HashMap<String, Collection<TestResult>>());

	public TestResultsSummary(QueryScenario queryscenario) {
		this.scenario = queryscenario;
	}

	public void cleanup() {
		failed_queries.clear();
		query_sets.clear();
		TestResults.clear();
		scenario=null;
	}

	public synchronized void addTest(String querySetID, TestResult result) {

		if (result == null) {
			System.err
					.println("Error - trying to add a null result set for querysetID: " + querySetID); //$NON-NLS-1$
			throw new RuntimeException(
					"Error - trying to add a null result set for querysetID: " + querySetID); //$NON-NLS-1$

		}
		Collection<TestResult> results = null;
		if (this.TestResults.containsKey(querySetID)) {
			results = this.TestResults.get(querySetID);
		} else {
			results = new ArrayList<TestResult>();
			this.TestResults.put(querySetID, results);
		}
		results.add(result);

	}

	public Collection<TestResult> getTests(String querySetID) {
		return this.TestResults.get(querySetID);
	}

	private static PrintStream getSummaryStream(String outputDir,
			String summaryName) throws IOException {
		File summaryFile = createSummaryFile(outputDir, summaryName);
		OutputStream os = new FileOutputStream(summaryFile);
		os = new BufferedOutputStream(os);
		return new PrintStream(os);
	}

	/**
	 * Overloaded to overwrite the already existing files
	 * @param outputDir 
	 * @param summaryName 
	 * @param overwrite 
	 * @return PrintStream
	 * @throws IOException 
	 */
	private static PrintStream getSummaryStream(String outputDir,
			String summaryName, boolean overwrite) throws IOException {

		// Check Extension is already specified for the file, if not add the
		// .txt
		if (summaryName.indexOf(".") == -1) { //$NON-NLS-1$
			summaryName = summaryName + ".txt"; //$NON-NLS-1$
		}

		File outputdir = new File(outputDir);
		if (!outputdir.exists()) {
			outputdir.mkdirs();
		}
		
		File summaryFile = new File(outputDir, summaryName);
		if (summaryFile.exists()) {
			if ( !overwrite) {
				throw new IOException(
					"Summary file already exists: " + summaryFile.getName()); //$NON-NLS-1$
			}
			summaryFile.delete();
		}
		
		try {
			summaryFile.createNewFile();
		} catch (IOException ioe) {
			ClientPlugin.LOGGER.error(ioe,"Error creating new summary file: "
					+ summaryFile.getAbsolutePath());
			throw ioe;
		}

		OutputStream os = new FileOutputStream(summaryFile);
		os = new BufferedOutputStream(os);
		return new PrintStream(os);
	}

	private static File createSummaryFile(String outputDir, String summaryName)
			throws IOException {
		File outputdir = new File(outputDir);
		if (!outputdir.exists()) {
			outputdir.mkdirs();
		}		
		File summaryFile = new File(outputDir, summaryName + ".txt"); //$NON-NLS-1$
		if (summaryFile.exists()) {
			ClientPlugin.LOGGER.error("Summary file already exists: " + summaryFile.getName()); //$NON-NLS-1$
			throw new IOException(
					"Summary file already exists: " + summaryFile.getName()); //$NON-NLS-1$
		}

		try {
			summaryFile.createNewFile();
		} catch (IOException e) {
			
			ClientPlugin.LOGGER.error("Failed to create summary file at: " + summaryFile.getAbsolutePath()); //$NON-NLS-1$
			throw new IOException(
					"Failed to create summary file at: " + summaryFile.getAbsolutePath() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return summaryFile;
	}

	private static Writer getOverallSummaryStream(String outputDir)
			throws IOException {
		boolean exists = false;
		File summaryFile = new File(outputDir, OVERALL_SUMMARY_FILE); //$NON-NLS-1$
		exists = summaryFile.exists();
		FileWriter fstream = new FileWriter(summaryFile, true);
		BufferedWriter out = new BufferedWriter(fstream);

		if (!exists) {

			try {
				summaryFile.createNewFile();
			} catch (IOException e) {
				ClientPlugin.LOGGER.error("Failed to create overall summary file at: " + summaryFile.getAbsolutePath()); //$NON-NLS-1$
				throw new IOException(
						"Failed to create overall summary file at: " + summaryFile.getAbsolutePath() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			printOverallSummaryHeadings(out);
		}

		return out;
	}

	private static void printOverallSummaryHeadings(Writer overallsummary) {

		try {
			overallsummary.write("================== \n"); //$NON-NLS-1$
			overallsummary.write("TestResult Summary \n"); //$NON-NLS-1$
			overallsummary.write("================== \n"); //$NON-NLS-1$

			overallsummary
					.write(pad("Scenario", 42, ' ') +
							"\t" + "Pass" + "\t" + "Fail" + "\t" + "Total" + "\t" + "Skipped" + "\n\n"); //$NON-NLS-1$

			overallsummary.flush();

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

	}

	private static Writer getOverallSummaryErrorsStream(String outputDir)
			throws IOException {
		boolean exists = false;
		File summaryFile = new File(outputDir, OVERALL_SUMMARY_ERROR_FILE); //$NON-NLS-1$
		exists = summaryFile.exists();
		FileWriter fstream = new FileWriter(summaryFile, true);
		BufferedWriter out = new BufferedWriter(fstream);

		if (!exists) {

			try {
				summaryFile.createNewFile();
			} catch (IOException e) {
				ClientPlugin.LOGGER.error("Failed to create overall summary error file at: " + summaryFile.getAbsolutePath()); //$NON-NLS-1$
				throw new IOException(
						"Failed to create overall summary error file at: " + summaryFile.getAbsolutePath() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			printOverallSummaryErrorHeadings(out);
		}

		return out;
	}

	private static void printOverallSummaryErrorHeadings(Writer overallsummary) {

		try {
			overallsummary.write("================== \n"); //$NON-NLS-1$
			overallsummary.write("TestResult Summary Errors \n"); //$NON-NLS-1$
			overallsummary.write("================== \n"); //$NON-NLS-1$

			overallsummary.write(pad("Scenario", 42, ' ') +
					"\t" + "Error \n\n"); //$NON-NLS-1$

			overallsummary.flush();

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

	}

	private void printQueryTests(PrintStream outputStream,
			Date testStartTS, Date endTS, Date length, int numberOfClients,
			SimpleDateFormat formatter, Collection<TestResult> results) {
		outputStream.println("Query TestResult Results [" + this.scenario.getResultsMode() + "]"); //$NON-NLS-1$
		outputStream.println("=================="); //$NON-NLS-1$
		outputStream.println("Start        Time: " + testStartTS); //$NON-NLS-1$
		outputStream.println("End          Time: " + endTS); //$NON-NLS-1$
		outputStream
				.println("Elapsed      Time: " + (length.getTime() / 1000) + " seconds"); //$NON-NLS-1$ //$NON-NLS-2$

		outputStream.println("Number of Clients: " + numberOfClients); //$NON-NLS-1$

		Map<String, String> passFailGenMap = getPassFailGen(results);
		outputStream
				.println("Number of Queries: " + passFailGenMap.get(MAP_QUERIES)); //$NON-NLS-1$ //$NON-NLS-2$
		outputStream
				.println("Number Passed    : " + passFailGenMap.get(MAP_PASS)); //$NON-NLS-1$ //$NON-NLS-2$
		outputStream
				.println("Of Pass, Number Expected Failures: " + passFailGenMap.get(MAP_EXP_FAIL)); //$NON-NLS-1$ //$NON-NLS-2$
		outputStream
				.println("Number Failed    : " + passFailGenMap.get(MAP_FAIL)); //$NON-NLS-1$ //$NON-NLS-2$

		Iterator<TestResult> resultItr = results.iterator();
		while (resultItr.hasNext()) {
			TestResult stat = resultItr.next();
			writeQueryResult(outputStream, formatter, stat);
		}

	}
	
	private static final String MAP_QUERIES = "queries";
	private static final String MAP_PASS = "pass";
	private static final String MAP_FAIL = "fail";
	private static final String MAP_EXP_FAIL = "expfail";
	

	private static Map<String, String> getPassFailGen(Collection<TestResult> results) {
		Map<String, String> passFailGenMap = new HashMap<String, String>();
		int queries = 0;
		int pass = 0;
		int fail = 0;
//		int gen = 0;
		int expected_fail = 0;

		for (Iterator<TestResult> resultsItr = results.iterator(); resultsItr.hasNext();) {
			TestResult stat = resultsItr.next();
			++queries;
			switch (stat.getStatus()) {
			case TestResult.RESULT_STATE.TEST_EXCEPTION:
				++fail;
				break;
			case TestResult.RESULT_STATE.TEST_SUCCESS:
				++pass;
				break;
			case TestResult.RESULT_STATE.TEST_EXPECTED_EXCEPTION:
				++pass;
				++expected_fail;
				break;
			}
		}
		passFailGenMap.put(MAP_QUERIES, Integer.toString(queries)); //$NON-NLS-1$
		passFailGenMap.put(MAP_PASS, Integer.toString(pass)); //$NON-NLS-1$
		passFailGenMap.put(MAP_FAIL, Integer.toString(fail)); //$NON-NLS-1$
		passFailGenMap.put(MAP_EXP_FAIL, Integer.toString(expected_fail)); //$NON-NLS-1$
		//       passFailGenMap.put("gen", Integer.toString(gen)); //$NON-NLS-1$
		return passFailGenMap;
	}

	private void addTotalPassFailGen(String scenario_name, Collection<TestResult> results,
			Date testStartTSx, Date endTSx, Date lengthTime) {
		int queries = 0;
		int pass = 0;
		int fail = 0;
		int succeed = 0;
		
		double avg;
		double totalFullMilliSecs = 0.0;


		String queryset = null;

		total_querysets++;
		for (Iterator<TestResult> resultsItr = results.iterator(); resultsItr.hasNext();) {
			TestResult stat = resultsItr.next();

			if (queryset == null) {
				queryset = stat.getQuerySetID();
			}

			++queries;
			switch (stat.getStatus()) {
			case TestResult.RESULT_STATE.TEST_EXCEPTION:
				++fail;

				String msg = 
					StringUtils.remove(
					StringUtils.remove(stat.getFailureMessage(), '\r'),
										'\n');
				
//				removeChars(stat.getExceptionMsg(),
//						new char[] { '\r', '\n' });

				this.failed_queries.add(stat.getQuerySetID() + "." + stat.getQueryID() + "~" + msg);
				break;
			case TestResult.RESULT_STATE.TEST_SUCCESS:
				++pass;
				++succeed;
				
				totalFullMilliSecs += ( stat.getEndTS() - stat.getBeginTS());
				
				break;
			case TestResult.RESULT_STATE.TEST_EXPECTED_EXCEPTION:
				++pass;
				break;
			}
		}
		
		avg = (succeed > 0 ? totalFullMilliSecs / succeed: -1.0);	

		this.query_sets.add("\t" + pad(queryset, 42, ' ') + "\t" + pass + "\t" + fail
				+ "\t" + queries + "\t" + (lengthTime.getTime() / 1000) + "\t\t" + avg);

		total_fail = total_fail + fail;
		total_pass = total_pass + pass;
		total_queries = total_queries + queries;

	}

	public void printResults(String querySetID,
			long beginTS, long endTS) throws Exception {

		ClientPlugin.LOGGER.debug("Print results for Query Set [" + querySetID + "]");

		try {
			printResults(querySetID, beginTS, endTS, 1, 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print test results.
	 * @param querySetID 
	 * 
	 * @param testStartTS
	 *            The test start time.
	 * @param endTS
	 *            The test end time.
	 * @param numberOfClients 
	 * @param runNumber 
	 * @throws Exception
	 */
	public void printResults(String querySetID,
			long testStartTS, long endTS, int numberOfClients, int runNumber)
			throws Exception {

		String testname = scenario.getQueryScenarioIdentifier();
		Collection<TestResult> TestResults = getTests(querySetID);
		// Properties props = scenario.getProperties();
		String outputDir = scenario.getTestRunDir();

		//       CombinedTestClient.log("Calculating and printing result statistics"); //$NON-NLS-1$
		if (TestResults == null) {
			// do nothing

		} else if (TestResults.size() > 0) {
			// Create output file
			String outputFileName = generateFileName(querySetID, scenario.getResultsMode(),
					System.currentTimeMillis());
			//           CombinedTestClient.log("Creating output file: " + outputFileName); //$NON-NLS-1$
			PrintStream outputStream = null;
			PrintStream overwriteStream = null;
			try {
				outputStream = getSummaryStream(outputDir, outputFileName);
				overwriteStream = getSummaryStream(outputDir, querySetID + "_" + scenario.getResultsMode(), true); //$NON-NLS-1$
			} catch (IOException e) {
				//              logError("Unable to get output stream for file: " + outputFileName); //$NON-NLS-1$
				throw e;
			}

			Date starttest = new Date(testStartTS);
			Date endtest = new Date(endTS);
			long diff = endtest.getTime() - starttest.getTime();

			total_seconds = total_seconds + diff;

			Date diffdate = new Date(diff);

			// endtest - starttest;
			//
			//		outputStream.println("Start        Time: " + new Date(testStartTS)); //$NON-NLS-1$
			//		outputStream.println("End          Time: " + new Date(endTS)); //$NON-NLS-1$
			// outputStream
			//			.println("Elapsed      Time: " + ((endTS - testStartTS) / 1000) + " seconds"); //$NON-NLS-1$ //$NON-NLS-2$

			addTotalPassFailGen(testname, TestResults, starttest, endtest,
					diffdate);
			// Text File output
			printQueryTests(outputStream, starttest, endtest, diffdate,
					numberOfClients, TestClient.TSFORMAT, TestResults);
			printQueryTests(overwriteStream, starttest, endtest,
					diffdate, numberOfClients, TestClient.TSFORMAT, TestResults);


			// Wiki Update
			//       	CombinedTestUtil.publishResultsToWiki(props, outputDir+File.separator+querySetID+".html", testStartTS, endTS, numberOfClients, TestResults); //$NON-NLS-1$ //$NON-NLS-2$

			// Print results according to test type
			// switch (CombinedTestClient.TEST_TYPE) {
			// case CombinedTestClient.TEST_TYPE_QUERY:
			// // Text File output
			// printQueryTests(outputStream, testStartTS, endTS,
			// numberOfClients, TestClientTransaction.TSFORMAT, TestResults);
			// printQueryTests(overwriteStream, testStartTS, endTS,
			// numberOfClients, TestClientTransaction.TSFORMAT, TestResults);
			//
			// // HTML Vesion of output
			//                	PrintStream htmlStream = getSummaryStream(outputDir, CONFIG_ID+".html", true); //$NON-NLS-1$
			// CombinedTestUtil.printHtmlQueryTests(htmlStream,
			// testStartTS, endTS, numberOfClients,
			// TestClientTransaction.TSFORMAT, TestResults);
			// htmlStream.close();
			//
			// // Wiki Update
			//                	CombinedTestUtil.publishResultsToWiki(props, outputDir+File.separator+CONFIG_ID+".html", testStartTS, endTS, numberOfClients, TestResults); //$NON-NLS-1$ //$NON-NLS-2$
			// break;
			// case CombinedTestClient.TEST_TYPE_LOAD:
			// CombinedTestUtil.printLoadTests(outputStream, testStartTS,
			// endTS, numberOfClients, TestClientTransaction.TSFORMAT,
			// TestResults);
			// CombinedTestUtil.printLoadTests(overwriteStream,
			// testStartTS, endTS, numberOfClients,
			// TestClientTransaction.TSFORMAT, TestResults);
			// break;
			// case CombinedTestClient.TEST_TYPE_PERF:
			// CombinedTestUtil.printPerfTests(outputStream, testStartTS,
			// endTS, numberOfClients, CONF_LVL, TestClientTransaction.TSFORMAT,
			// TestResults);
			// CombinedTestUtil.printPerfTests(overwriteStream,
			// testStartTS, endTS, numberOfClients, CONF_LVL,
			// TestClientTransaction.TSFORMAT, TestResults);
			// break;
			// case CombinedTestClient.TEST_TYPE_PROF:
			// CombinedTestUtil.printProfTests();
			// break;
			// default:
			// break;
			// }

			//        CombinedTestClient.log("Closing output stream"); //$NON-NLS-1$
			outputStream.close();
			overwriteStream.close();
		} else {
			//          logError("No results to print."); //$NON-NLS-1$
		}
	}

	/**
	 * This method prints an exception to the {@code Summary_connection_exception_errors} file.
	 * 
	 * @param ex the exception that occurred while connection to the JDV server
	 * @throws IOException
	 */
	public void printServerConnectionException(Exception ex) throws IOException{
		String scenarioName = scenario.getQueryScenarioIdentifier();
		Writer outputWriter = null;
		try {
			outputWriter = getConnectionExceptionSummaryStream(scenario.getOutputDir()); //$NON-NLS-1$
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
		
		try{
			outputWriter.write(pad(scenarioName, 42, ' '));
			outputWriter.write("\t");
			ex.printStackTrace(new PrintWriter(outputWriter));
			outputWriter.write(System.getProperty("line.separator"));
			outputWriter.write("-------------------------------------");
			outputWriter.write(System.getProperty("line.separator"));
			
			outputWriter.flush();
		} catch (IOException ioex){
			ioex.printStackTrace();
			try{
				outputWriter.close();
			} catch (IOException e){
				// ignore
			}
			throw ioex;
		}
	}
	
	/**
	 * Returns stream for writing the server-connection-exception.
	 * 
	 * @param outputDir
	 * @return
	 * @throws IOException
	 */
	private static Writer getConnectionExceptionSummaryStream(String outputDir)
			throws IOException {
		boolean exists = false;
		File summaryFile = new File(outputDir, CONNECTION_EXCEPTION_SUMMARY_ERROR_FILE);
		exists = summaryFile.exists();
		FileWriter fstream = new FileWriter(summaryFile, true);
		BufferedWriter out = new BufferedWriter(fstream);

		if (!exists) {

			try {
				summaryFile.createNewFile();
			} catch (IOException e) {
				ClientPlugin.LOGGER.error("Failed to create overall connection-exception-summary-errors file at: " + summaryFile.getAbsolutePath()); //$NON-NLS-1$
				throw new IOException(
						"Failed to create overall summary file at: " + summaryFile.getAbsolutePath() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			printOverallConnectionExceptionSummaryErrosHeadings(out);
		}

		return out;
	}
	
	/**
	 * Prints head of the server-connection-exception file.
	 * 
	 * @param overallsummary
	 */
	private static void printOverallConnectionExceptionSummaryErrosHeadings(Writer overallsummary) {

		try {
			overallsummary.write("================== \n"); //$NON-NLS-1$
			overallsummary.write("TestResult Summary Connection Errors \n"); //$NON-NLS-1$
			overallsummary.write("================== \n"); //$NON-NLS-1$

			overallsummary
					.write(pad("Scenario", 42, ' ') +
							"\tError\n\n"); //$NON-NLS-1$

			overallsummary.flush();

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

	}
	
	/**
	 * Prints summary to the {@code Summary_totals}, {@code Summary_errors} and {@code Summary_<quert_set>_<scenario_name>}
	 * files.
	 *  
	 * @param expectedQueryCount expected number of queries that should run
	 * @throws Exception
	 */
	public void printTotals(int expectedQueryCount) throws Exception {
		// String outputDir = scenario.getResultsGenerator().getOutputDir();
		String scenario_name = scenario.getQueryScenarioIdentifier();
		String querysetname = scenario.getQuerySetName();

		String summarydir = scenario.getOutputDir();

		PrintStream outputStream = null;
		Writer overallsummary = null;
		Writer overallsummaryerrors = null;
		try {
			outputStream = getSummaryStream(summarydir,
					"Summary_" + querysetname + "_" + scenario_name, true); //$NON-NLS-1$

			overallsummary = getOverallSummaryStream(summarydir);
			overallsummaryerrors = getOverallSummaryErrorsStream(summarydir);
		} catch (IOException e) {
			e.printStackTrace();
			//              logError("Unable to get output stream for file: " + outputFileName); //$NON-NLS-1$
			throw e;
		}

		outputStream
				.println("Scenario " + scenario_name + " Summary [" + this.scenario.getResultsMode() + "]"); //$NON-NLS-1$
		outputStream.println("Query Set Name: " + querysetname); //$NON-NLS-1$
		outputStream.println("=================="); //$NON-NLS-1$

		outputStream.println("Number of test sets: " + total_querysets); //$NON-NLS-1$ //$NON-NLS-2$

		outputStream.println("=================="); //$NON-NLS-1$
		outputStream
				.println("\t" + pad("Name", 42, ' ') +  
						"\t" + "Pass" + "\t" + "Fail" + "\t" + "Total" + "\t" + "Time(sec)" + "\t"+ " Avg(mils)"); //$NON-NLS-1$

		if (!this.query_sets.isEmpty()) {
			// sort so that like failed queries are show together
			Collections.sort(this.query_sets);

			for (Iterator<String> it = this.query_sets.iterator(); it.hasNext();) {
				outputStream.println(it.next()); //$NON-NLS-1$ //$NON-NLS-2$
			}

		}
		outputStream.println("=================="); //$NON-NLS-1$

		outputStream.println("\t" + pad("Totals", 42, ' ')
				+ "\t" + total_pass + "\t"
				+ total_fail + "\t" + total_queries + "\t" +  (total_seconds / 1000) );

		try {
			overallsummary.write(pad(scenario_name, 42, ' ') + " \t"
					+ total_pass + "\t" + total_fail + "\t" + total_queries + "\t" + (expectedQueryCount - total_queries)  
					+ "\n");
			overallsummary.flush();

		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally { // always close the file
			try {
				overallsummary.close();
			} catch (IOException ioe2) {
				// just ignore it
			}
		} //

		// outputStream
		//			.println("Number of Queries: " + total_queries); //$NON-NLS-1$ //$NON-NLS-2$
		// outputStream
		//			.println("Number Passed    : " + total_pass); //$NON-NLS-1$ //$NON-NLS-2$
		// outputStream
		//			.println("Number Failed    : " + total_fail); //$NON-NLS-1$ //$NON-NLS-2$

		if (!this.failed_queries.isEmpty()) {
			// sort so that like failed queries are show together
			Collections.sort(this.failed_queries);

			outputStream.println("\n\n=================="); //$NON-NLS-1$
			outputStream.println("Failed Queries"); //$NON-NLS-1$	

			overallsummaryerrors.write("\n" + scenario_name + "\n");

			for (Iterator<String> it = this.failed_queries.iterator(); it
					.hasNext();) {
				String error = it.next();
				outputStream.println("\t - " + error); //$NON-NLS-1$ //$NON-NLS-2$
				// write all errors to the summary file
				overallsummaryerrors.write("\t\t" + error + "\n");

			}

			try {
				overallsummaryerrors.flush();

			} catch (IOException ioe) {
				ioe.printStackTrace();
			} finally { // always close the file
				try {
					overallsummaryerrors.close();
				} catch (IOException ioe2) {
					// just ignore it
				}
			} //

			outputStream.println("=================="); //$NON-NLS-1$

		}

		outputStream.close();

	}

	private static String pad(String src, int padTo, char padChar) {
		int numPad = padTo - src.length();
		if (numPad > 0) {
			StringBuffer sb = new StringBuffer();
			char[] pad = new char[numPad];
			Arrays.fill(pad, padChar);
			sb.append(src);
			sb.append(pad);
			return sb.toString();
		}

		return src;

	}

	private static String generateFileName(String configName, String resultmode, long timestamp) {
		return configName + "_" + resultmode
				+ "_" + FILE_NAME_DATE_FORMATER.format(new Date(timestamp));
		//+ "_Run-" + runNumber; //$NON-NLS-1$ //$NON-NLS-2$
	}

//	private static void printHtmlQueryTests(PrintStream outputStream,
//			long testStartTS, long endTS, int numberOfClients,
//			SimpleDateFormat formatter, Collection results) {
//
//		StringBuffer htmlCode = new StringBuffer("<html>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<HEAD>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<TITLE>Query TestResult Results</TITLE>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<STYLE TYPE=\"text/css\">").append(NL); //$NON-NLS-1$
//		htmlCode.append(
//				"td { font-family: \"New Century Schoolbook\", Times, serif  }").append(NL); //$NON-NLS-1$
//		htmlCode.append("td { font-size: 8pt }").append(NL); //$NON-NLS-1$
//		htmlCode.append("</STYLE>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<SCRIPT type=\"text/javascript\">").append(NL); //$NON-NLS-1$
//		htmlCode.append("var scriptWin = null;").append(NL); //$NON-NLS-1$
//		htmlCode.append("function show(msg){").append(NL); //$NON-NLS-1$
//		//htmlCode.append("alert(msg);").append(nl);       //$NON-NLS-1$
//		htmlCode.append("if (scriptWin == null || scriptWin.closed){").append(NL); //$NON-NLS-1$
//		htmlCode.append(
//				"scriptWin = window.open(\"\", \"script\", \"width=800,height=50,resizable\");").append(NL); //$NON-NLS-1$
//		htmlCode.append("scriptWin.document.open(\"text/plain\");").append(NL); //$NON-NLS-1$
//		htmlCode.append("}").append(NL); //$NON-NLS-1$
//		htmlCode.append("scriptWin.focus();").append(NL); //$NON-NLS-1$
//		htmlCode.append("msg = msg.replace(/#/g, '\"');").append(NL); //$NON-NLS-1$
//		htmlCode.append("scriptWin.document.writeln(msg);").append(NL); //$NON-NLS-1$        
//		htmlCode.append("}").append(NL); //$NON-NLS-1$        
//		htmlCode.append("</SCRIPT>").append(NL); //$NON-NLS-1$        
//		htmlCode.append("</HEAD>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<body>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<h1>Query TestResult Results</h1>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<table border=\"1\">").append(NL); //$NON-NLS-1$
//
//		addTableRow(htmlCode, "StartTime", new Date(testStartTS).toString()); //$NON-NLS-1$
//		addTableRow(htmlCode, "EndTime", new Date(endTS).toString()); //$NON-NLS-1$
//		addTableRow(htmlCode,
//				"Elapsed Time", ((endTS - testStartTS) / 1000) + " seconds"); //$NON-NLS-1$ //$NON-NLS-2$
//		addTableRow(htmlCode,
//				"Number Of Clients", String.valueOf(numberOfClients)); //$NON-NLS-1$
//
//		Map passFailGenMap = getPassFailGen(results);
//		addTableRow(htmlCode,
//				"Number of Queries:", passFailGenMap.get("queries")); //$NON-NLS-1$ //$NON-NLS-2$
//		addTableRow(htmlCode, "Number Passed    :", passFailGenMap.get("pass")); //$NON-NLS-1$ //$NON-NLS-2$
//		addTableRow(htmlCode, "Number Failed    :", passFailGenMap.get("fail")); //$NON-NLS-1$ //$NON-NLS-2$
//		//       addTableRow(htmlCode, "Number Generated :", passFailGenMap.get("gen")); //$NON-NLS-1$ //$NON-NLS-2$
//
//		ResponseTimes responseTimes = calcQueryResponseTimes(results);
//		addTableRow(htmlCode, "QPS :", Double.toString(responseTimes.qps)); //$NON-NLS-1$ 
//		//        addTableRow(htmlCode, "Ave First Resp   :", Double.toString(responseTimes.first)); //$NON-NLS-1$ 
//		//        addTableRow(htmlCode, "Ave Full Resp    :", Double.toString(responseTimes.full)); //$NON-NLS-1$ 
//
//		htmlCode.append("</table> <p>").append(NL); //$NON-NLS-1$
//		htmlCode.append("<table border=\"1\">").append(NL); //$NON-NLS-1$
//
//		// Add table headers
//		htmlCode.append("<tr style=\"background: #C0C0C0 \">"); //$NON-NLS-1$
//
//		addTableData(htmlCode, "QueryId"); //$NON-NLS-1$
//		addTableData(htmlCode, "Result"); //$NON-NLS-1$
//		addTableData(htmlCode, "First Response"); //$NON-NLS-1$
//		addTableData(htmlCode, "Total Seconds"); //$NON-NLS-1$
//		addTableData(htmlCode, "Exception"); //$NON-NLS-1$
//		addTableData(htmlCode, "Error File (if any)"); //$NON-NLS-1$
//		htmlCode.append("</tr>").append(NL); //$NON-NLS-1$
//
//		Iterator resultItr = results.iterator();
//		while (resultItr.hasNext()) {
//			TestResult stat = (TestResult) resultItr.next();
//			htmlCode.append("<tr>").append(NL); //$NON-NLS-1$            
//			addTableDataLink(htmlCode, stat.getQueryID(),
//					"show('" + scrub(stat.getQuery()) + "')"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
//			addTableData(htmlCode, stat.getResultStatusString(),
//					"fail".equalsIgnoreCase(stat.getResultStatusString())); //$NON-NLS-1$
//			addTableData(htmlCode, new Date(stat.getBeginTS()).toString());
//
//			// Long.toString(stat.getBeginTS()));
//			addTableData(htmlCode,
//					Long.toString((stat.getEndTS() - stat.getBeginTS() / 1000)));
//			// Long.toString(stat.getEndTS()));
//			if (stat.getStatus() == TestResult.RESULT_STATE.TEST_EXCEPTION) {
//				addTableData(htmlCode, stat.getFailureMessage());
//				if (stat.getErrorfile() != null
//						&& !stat.getErrorfile().equals("null")) { //$NON-NLS-1$
//					addTableDataLink(htmlCode, stat.getErrorfile(), ""); //$NON-NLS-1$ 
//				} else {
//					addTableData(htmlCode, ""); //$NON-NLS-1$
//				}
//			} else {
//				addTableData(htmlCode, ""); //$NON-NLS-1$
//				addTableData(htmlCode, ""); //$NON-NLS-1$                                
//			}
//			htmlCode.append("</tr>").append(NL); //$NON-NLS-1$
//		}
//		htmlCode.append("</table>").append(NL); //$NON-NLS-1$
//		outputStream.print(htmlCode.toString());
//	}

//	private static void addTableRow(StringBuffer table, String column,
//			Object msg) {
//		table.append("<tr>").append(NL); //$NON-NLS-1$        
//		addTableData(table, column); //$NON-NLS-1$
//		addTableData(table, msg.toString());
//		table.append("</tr>").append(NL); //$NON-NLS-1$        
//	}

//	private static void addTableData(StringBuffer table, String msg) {
//		addTableData(table, msg, false);
//	}

//	private static void addTableDataLink(StringBuffer table, String link,
//			String jsEvent) {
//		if (link.indexOf(".") == -1) //$NON-NLS-1$
//			table.append("<td>").append("<a href=\"#" + link + "\" onclick=\"" + jsEvent + "\">" + link + "</a>").append("</td>").append(NL); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
//		else
//			table.append("<td>").append("<a href=\"" + link + "\" onclick=\"" + jsEvent + "\">" + link + "</a>").append("</td>").append(NL); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$         
//	}

//	private static void addTableData(StringBuffer table, String msg,
//			boolean error) {
//		if (error)
//			table.append("<td style=\"background: #ffccff \">").append(msg).append("</td>").append(NL); //$NON-NLS-1$ //$NON-NLS-2$
//		else
//			table.append("<td>").append(msg).append("</td>").append(NL); //$NON-NLS-1$ //$NON-NLS-2$
//	}

//	/**
//	 * @param queryResults 
//	 * @return ResponseTimes
//	 * @since 4.2
//	 */
//	private static ResponseTimes calcQueryResponseTimes(Collection<TestResult> queryResults) {
//		ResponseTimes responseTimes = new ResponseTimes();
//		int nQueries = 0;
//		double startTS;
//		// double firstResponseTimeStamp;
//		double fullResponseTimeStamp;
//		double totalSecs = 0.0;
//		double totalFullMilliSecs = 0.0;
//		// double totalFirstMilliSecs = 0.0;
//
//		for (Iterator<TestResult> resultItr = queryResults.iterator(); resultItr.hasNext();) {
//			TestResult result = resultItr.next();
//			if ( result.getException() != null || result.getFailureMessage() != null) {
//				// dont include errors in time calculations;
//				continue;		
//			}
//			++nQueries;
//
//			startTS = result.getBeginTS();
//			// firstResponseTimeStamp = result.getBeginTS();
//			fullResponseTimeStamp = result.getEndTS();
//			totalSecs += ((fullResponseTimeStamp - startTS) / 1000);
//
//			// totalFirstMilliSecs += (firstResponseTimeStamp - startTS);
//			totalFullMilliSecs += (fullResponseTimeStamp - startTS);
//		}
//
//		responseTimes.qps = (totalSecs > 0 ? nQueries / totalSecs : -1.0);
//		// responseTimes.first = (nQueries > 0 ? totalFirstMilliSecs / nQueries
//		// : -1.0);
//		responseTimes.full = (nQueries > 0 ? totalFullMilliSecs / nQueries
//				: -1.0);
//		return responseTimes;
//	}

//	private static String scrub(String str) {
//		// Scrub the query
//		if (str != null) {
//			str = str.replace('"', '#');
//			str = str.replace('\'', '#');
//		}
//		return str;
//	}

	/**
	 * @param outputStream
	 * @param formatter
	 * @param stat
	 */
	private static void writeQueryResult(PrintStream outputStream,
			SimpleDateFormat formatter, TestResult stat) {
		
		outputStream.print(stat.getQueryID());
		outputStream.print(","); //$NON-NLS-1$
		outputStream.print(stat.getResultStatusString());
		outputStream.print(","); //$NON-NLS-1$
//		outputStream.print(stat.getBeginTS());
//		outputStream.print(","); //$NON-NLS-1$
//		outputStream.print(stat.getEndTS());
//		outputStream.print(","); //$NON-NLS-1$
		outputStream.print(getFormattedTimestamp(formatter, stat.getBeginTS()));
		//        outputStream.print(","); //$NON-NLS-1$
		// outputStream.print(getFormattedTimestamp(formatter,
		// stat.getFirstRepsonseTimeStamp()));
		outputStream.print(","); //$NON-NLS-1$
		outputStream.print(getFormattedTimestamp(formatter, stat.getEndTS()));
		outputStream.print(","); //$NON-NLS-1$
		outputStream.print(String.valueOf(stat.getExecutionTime()));
		outputStream.print(","); //$NON-NLS-1$

		outputStream
				.println((stat.getStatus() != TestResult.RESULT_STATE.TEST_SUCCESS ? stat
						.getFailureMessage() : "")); //$NON-NLS-1$
	}

	private static String getFormattedTimestamp(SimpleDateFormat format,
			long millis) {
		return format.format(new Date(millis));
	}

//	private static class ResponseTimes {
//		double first; // millis
//		double full; // millis
//		double qps; // secs
//	}

}
