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

package org.jboss.bqt.client.xml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.jboss.bqt.client.ClientPlugin;
import org.jboss.bqt.client.QuerySQL;
import org.jboss.bqt.client.QueryTest;
import org.jboss.bqt.client.results.ExpectedResultsHolder;
import org.jboss.bqt.client.xml.QueryResults.ColumnInfo;
import org.jboss.bqt.core.exception.TransactionRuntimeException;
import org.jboss.bqt.core.util.ExceptionUtil;
import org.jboss.bqt.core.util.ObjectConverterUtil;
import org.jboss.bqt.core.util.StringHelper;
import org.jboss.bqt.core.xml.SAXBuilderHelper;
import org.jboss.bqt.jdbc.sql.lang.ElementSymbol;
import org.jboss.bqt.jdbc.sql.lang.Select;
import org.jboss.bqt.jdbc.sql.lang.SelectSymbol;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.IllegalDataException;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;


/**
 * <P> This program helps in parsing XML Query and Results files into
 * map objects containing individual queries/ResultSets</P>
 *
 * <P> This program is useful to convert the JDBC ResultSet objects into
 * XML format. We physically walk through the ResultSet object and use JDOM to
 * convert the ResultSets into XML. This also helps convert Exceptions into XML
 * format.</P>
 */

public class XMLQueryVisitationStrategy {

    //the row from which we start converting ResultSets to XML
    private static final int START_ROW = 1;

    public XMLQueryVisitationStrategy() {
    }

    /**
     * Consume an XML Query File and produce a Map containing queries, with
     * queryNames/IDs as Keys.
     * <br>
     * @param queryScenarioID 
     * @param queryFile the XML file object that is to be parsed
     * @param querySetID 
     * @return the List containing quers.
     * @throws IOException 
     * @exception JDOMException if there is an error consuming the message.
     */
    public List<QueryTest> parseXMLQueryFile(String queryScenarioID, File queryFile, String querySetID) throws IOException, JDOMException {

    	List<QueryTest> queries = new LinkedList<QueryTest>();
        SAXBuilder builder = SAXBuilderHelper.createSAXBuilder(false);
        Document queryDocument = builder.build(queryFile);
        List<Element> queryElements = queryDocument.getRootElement().getChildren(TagNames.Elements.QUERY);
        Iterator<Element> iter = queryElements.iterator();
        while ( iter.hasNext() ) {
            Element queryElement = iter.next();
            String queryName = queryElement.getAttributeValue(TagNames.Attributes.NAME);
            Element exceptionElement = queryElement.getChild(TagNames.Elements.EXCEPTION);
            if ( exceptionElement == null ) {
	        	String uniqueID = querySetID + "_" + queryName;
	        	
				List<Element> parmChildren = queryElement.getChildren(TagNames.Elements.SQL);
		        	
				if (parmChildren == null || parmChildren.isEmpty()) {
					ClientPlugin.LOGGER.debug("=======  Creating Single QueryTest " + queryName);
	        	    QuerySQL sql = createQuerySQL(queryElement);
	         	    
	        	    QueryTest q = new QueryTest(queryScenarioID, querySetID, queryName, new QuerySQL[] {sql});
	        	    queries.add(q);
	        	} else {
	        		ClientPlugin.LOGGER.debug("=======  Creating QueryTest has multiple sql statements " + queryName);
	         		QuerySQL[] querysql = new QuerySQL[parmChildren.size()];
	        		int c = 0;
	        		
	        		final Iterator<Element> sqliter = parmChildren.iterator();
	        		while ( sqliter.hasNext() ) {
	        			final Element sqlElement = (Element) sqliter.next();
	        			QuerySQL sql = createQuerySQL(sqlElement);
	        			querysql[c] = sql;
	        			c++;	
	        		}
	        		QueryTest q = new QueryTest(queryScenarioID, querySetID, uniqueID, querysql);
	        		queries.add(q);
	 	    
	        	}
	
            } else {
                
                String exceptionType = exceptionElement.getChild(TagNames.Elements.CLASS).getTextTrim();
                
                String uniqueID = querySetID + "_" + queryName;
                QuerySQL sql = new QuerySQL(exceptionType, null);
                
                QueryTest q = new QueryTest(queryScenarioID, uniqueID, querySetID, new QuerySQL[] {sql});
                queries.add(q);

            }
        } // end of while
        return queries;
    }
    
    private QuerySQL createQuerySQL(Element queryElement) {
 	    String query = queryElement.getTextTrim();
 	    	    
	    Object[] parms = getParms(queryElement);
	    	    
	    QuerySQL sql = new QuerySQL(query, parms);
	    
	    Serializable payload = createPayLoad(queryElement);
	    if (payload != null) sql.setPayLoad(payload);
	    
 	    String updateCnt = queryElement.getAttributeValue(TagNames.Attributes.UPDATE_CNT);
 	    if (updateCnt != null && updateCnt.trim().length() > 0) {
 	    	int cnt = Integer.parseInt(updateCnt);
 	    	sql.setUpdateCnt(cnt);
 	    }
 	    
 	    String rowCnt = queryElement.getAttributeValue(TagNames.Attributes.TABLE_ROW_COUNT);
 	    if (rowCnt != null && rowCnt.trim().length() > 0) {
 	    	int cnt = Integer.parseInt(rowCnt);
 	    	sql.setRowCnt(cnt);
 	    }
 	    
 	    String numTimes = queryElement.getAttributeValue(TagNames.Attributes.EXECUTE_NUM_TIMES);
 	    if (numTimes != null && numTimes.trim().length() > 0) {
 	    	int cnt = Integer.parseInt(numTimes);
 	    	if (cnt > 0)
 	    		sql.setRunTimes(cnt);
 	    } 	    
	    
	    return sql;	
    }
    
    private Serializable createPayLoad(Element parent) {
		List<Element> parmChildren = parent.getChildren(TagNames.Elements.PAYLOAD);
		if (parmChildren == null) {
		    return null;
		}
		
		Properties props = new Properties();
		
		final Iterator<Element> iter = parmChildren.iterator();
		while ( iter.hasNext() ) {
			final Element parmElement = iter.next();
			
			String name = parmElement.getAttributeValue(TagNames.Attributes.NAME);
			String value = parmElement.getTextTrim();
			props.setProperty(name, value);
		}
		
		return props;
    }
    
    private Object[] getParms(Element parent) {
		List<Element> parmChildren = parent.getChildren(TagNames.Elements.PARM);
		if (parmChildren == null) {
		    return null;
		}
		
		Object[] parms = new Object[parmChildren.size()];
		int i = 0;
		final Iterator<Element> iter = parmChildren.iterator();
		while ( iter.hasNext() ) {
			final Element parmElement = iter.next();
			try {
			    Object parm = createParmType(parmElement);
			    parms[i] = parm;
			    i++;
			} catch (JDOMException e) {
			    throw new TransactionRuntimeException(e);
			}		
		}
		
		
		
		return parms;
    }
    
    private Object createParmType(Element cellElement) throws JDOMException {

        Object cellObject = null;
        
        final String typeName = cellElement.getAttributeValue(TagNames.Attributes.TYPE);
 
        if ( typeName.equalsIgnoreCase(TagNames.Elements.BOOLEAN) ) {
            cellObject = consumeMsg((Boolean) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.STRING) ) {
            cellObject = consumeMsg((String) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.CHAR) ) {
            cellObject = consumeMsg((Character) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.BYTE) ) {
            cellObject = consumeMsg((Byte) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.DOUBLE) ) {
            cellObject = consumeMsg((Double) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.DATE) ) {
            cellObject = consumeMsg((java.sql.Date) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.TIME) ) {
            cellObject = consumeMsg((Time) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.TIMESTAMP) ) {
            cellObject = consumeMsg((Timestamp) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.FLOAT) ) {
            cellObject = consumeMsg((Float) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.BIGDECIMAL) ) {
            cellObject = consumeMsg((BigDecimal) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.BIGINTEGER) ) {
            cellObject = consumeMsg((BigInteger) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.INTEGER) ) {
            cellObject = consumeMsg((Integer) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.LONG) ) {
            cellObject = consumeMsg((Long) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.SHORT) ) {
            cellObject = consumeMsg((Short) cellObject, cellElement);
        } else if ( typeName.equalsIgnoreCase(TagNames.Elements.OBJECT) ) {
            cellObject = consumeMsg((String) cellObject, cellElement);
        }

        return cellObject;
    }

    /**
     * Consume an XML results File and produce a Map containing query results
     * as List objects, with resultNames/IDs as Keys.
     * <br>
     * @param test 
     * @param querySetID Identifies the query set
     * @param resultsFile the XML file object that is to be parsed
     * @return the Map containig results.
     * @throws IOException 
     * @exception JDOMException if there is an error consuming the message.
     */
    public ExpectedResultsHolder parseXMLResultsFile(final QueryTest test, final String querySetID, final File resultsFile) throws IOException, JDOMException {

        QueryResults queryResults;
        ExpectedResultsHolder expectedResults = null;

        final SAXBuilder builder = SAXBuilderHelper.createSAXBuilder(false);
        final Document resultsDocument = builder.build(resultsFile);
        final String query = resultsDocument.getRootElement().getChildText(TagNames.Elements.QUERY);
        final List<Element> resultElements = resultsDocument.getRootElement().getChildren(TagNames.Elements.QUERY_RESULTS);
        final Iterator<Element> iter = resultElements.iterator();
        while ( iter.hasNext() ) {
            final Element resultElement = iter.next();
//            final String resultName = resultElement.getAttributeValue(TagNames.Attributes.NAME);
            
            final String execTime  = resultElement.getAttributeValue(TagNames.Attributes.EXECUTION_TIME);
            
            queryResults = consumeMsg(new QueryResults(), resultElement);
            if ( queryResults.getFieldCount() != 0 ) {
                //
                // We've got a ResultSet
                //
                expectedResults = new ExpectedResultsHolder( TagNames.Elements.QUERY_RESULTS, test );
  //               expectedResults.setQueryID( resultName );
                expectedResults.setQuery(query);
                if (execTime != null && execTime.trim().length() > 0) expectedResults.setExecutionTime( Long.parseLong(execTime) );
                expectedResults.setIdentifiers( queryResults.getFieldIdents() );
                expectedResults.setTypes( queryResults.getTypes() );
                if ( queryResults.getRecordCount() > 0 ) {
                    expectedResults.setRows(queryResults.getRecords());
                }
            } else {
                final Element exceptionElement = resultElement.getChild(TagNames.Elements.EXCEPTION);
                if ( exceptionElement != null ) {
                	//
                    // We've got an exception
                    //
                    expectedResults = new ExpectedResultsHolder(TagNames.Elements.EXCEPTION, test);
                    expectedResults.setQuery(query);
                    
                    expectedResults.setExceptionClassName(exceptionElement.getChild(TagNames.Elements.CLASS).getTextTrim());
                    String msg = null;
                    if (exceptionElement.getChild(TagNames.Elements.MESSAGE) != null) {
                    	msg = exceptionElement.getChild(TagNames.Elements.MESSAGE).getTextTrim();  
                    } else if (exceptionElement.getChild(TagNames.Elements.MESSAGE_STARTSWITH) != null ) {
                    	msg = exceptionElement.getChild(TagNames.Elements.MESSAGE_STARTSWITH).getTextTrim(); 
                    	expectedResults.setExceptionStartsWith(true);
                    } else if (exceptionElement.getChild(TagNames.Elements.MESSAGE_CONTAINS) != null ) {
                        msg = exceptionElement.getChild(TagNames.Elements.MESSAGE_CONTAINS).getTextTrim(); 
						expectedResults.setExceptionContains(true);
					} else if (exceptionElement.getChild(TagNames.Elements.MESSAGE_REGEX) != null) {
						msg = exceptionElement.getChild(TagNames.Elements.MESSAGE_REGEX).getTextTrim();
						expectedResults.setExceptionRegex(true);
					}

                    expectedResults.setExceptionMsg(StringUtils.remove(msg, '\r'));
                } else {
                	//
                	// No result.
                	//
                	expectedResults = new ExpectedResultsHolder( TagNames.Elements.QUERY_RESULTS,  test );
                    expectedResults.setQuery(query);
                }
            }
        }
        return expectedResults;
    }

    /**
     * Consume an XML results File, produce results as JDOM and add results to the given parent.
     * <br>
     * @param resultsFile the XML file object that is to be parsed
     * @param parent the parent Element to assign results to
     * @return the modified parent
     * @throws IOException 
     * @exception JDOMException if there is an error consuming the message.
     */
    public Element parseXMLResultsFile(File resultsFile, Element parent) throws IOException, JDOMException {

        SAXBuilder builder = SAXBuilderHelper.createSAXBuilder(false);
        Document resultsDocument = builder.build(resultsFile);
        List<Element> resultElements = resultsDocument.getRootElement().getChildren(TagNames.Elements.QUERY_RESULTS);
        Iterator<Element> iter = resultElements.iterator();
        while ( iter.hasNext() ) {
            Element resultElement = iter.next();
            if ( resultElement.getChild(TagNames.Elements.SELECT) == null ) {
                // We've got an exception
                Element exceptionElement = resultElement.getChild(TagNames.Elements.EXCEPTION);
                if ( exceptionElement != null ) {
                    // ---------------------------------
                    // Add the ExceptionType element ...
                    // ---------------------------------
                    Element typeElement = new Element(TagNames.Elements.EXCEPTION_TYPE);
                    typeElement.setText(exceptionElement.getChild(TagNames.Elements.EXCEPTION_TYPE).getTextTrim());
                    parent.addContent(typeElement);

                    // ---------------------------
                    // Add the Message element ...
                    // ---------------------------
                    Element messageElement = new Element(TagNames.Elements.MESSAGE);    
                    String msg = exceptionElement.getChild(TagNames.Elements.MESSAGE).getTextTrim();   
                    
                    messageElement.setText(StringUtils.remove(msg, '\r'));
                    parent.addContent(messageElement);

                    // -------------------------
                    // Add the Class element ...
                    // -------------------------
                    Element classElement = new Element(TagNames.Elements.CLASS);
                    classElement.setText(exceptionElement.getChild(TagNames.Elements.CLASS).getTextTrim());
                    parent.addContent(classElement);
                }
            } else {
                // We've got results

                // -------------------------------
                // Read the SELECT elements
                // -------------------------------
                Element selectElement = resultElement.getChild(TagNames.Elements.SELECT);
                resultElement.removeChild(TagNames.Elements.SELECT);
                parent.addContent(selectElement);

                // -------------------------------
                // Read the TABLE of data
                // -------------------------------
                Element tableElement = resultElement.getChild(TagNames.Elements.TABLE);
                resultElement.removeChild(TagNames.Elements.TABLE);
                parent.addContent(tableElement);
            }
        }
        return parent;
    }

    /*********************************************************************************************
     *********************************************************************************************
     CONSUME METHODS
     *********************************************************************************************
     ********************************************************************************************/

    /**
     * GenerateExpectedResults XML for an exception in Object form.
     *
     * @param ex
     * @param exceptionElement
     * @return The JDOM exception element.
     */
    public static Element jdomException(Throwable ex, Element exceptionElement) {
        // ---------------------------------
        // Add the ExceptionType element ...
        // ---------------------------------
        String className = ex.getClass().getName();
        int index = className.lastIndexOf('.');
        if ( index != -1 && (++index) < className.length() ) {
            className = className.substring(index);
        }
        Element typeElement = new Element(TagNames.Elements.EXCEPTION_TYPE);
        typeElement.setText(className);
        exceptionElement.addContent(typeElement);

        // ---------------------------
        // Add the Message element ...
        // ---------------------------

		Element messageElement = new Element(TagNames.Elements.MESSAGE);
		messageElement.setText(ExceptionUtil.getExceptionMessage(ex));
		exceptionElement.addContent(messageElement);

        // -------------------------
        // Add the Class element ...
        // -------------------------
        Element classElement = new Element(TagNames.Elements.CLASS);
        classElement.setText(ex.getClass().getName());
		exceptionElement.addContent(classElement);

		// ------------------------------
		// Add the StackTrace element ...
		// ------------------------------
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));

		Element stackTraceElement = new Element(TagNames.Elements.STACK_TRACE);
		stackTraceElement.setText(sw.toString());
		exceptionElement.addContent(stackTraceElement);

		return exceptionElement;
	}

	/**
	 * Fills expected-result XML data element with specified String value. The method will take care about possible
	 * unprintable characters.
	 * @param data string to save
	 * @param element XML element
	 */
	private static void fillDataElement(String data, Element element) {
		try {
			element.setText(data);
		} catch (IllegalDataException e) {
			element.setAttribute(TagNames.Attributes.UNPRINTABALE, TagNames.Values.TRUE);
			element.setAttribute(TagNames.Attributes.HEXVALUE, StringHelper.encodeHex(data));
		}
	}

	/**
	 * Parses expected-result XML data element. The method may load and convert unprintable hexadecimal value.
	 * @param element XML element to read
	 * @return read string
	 */
	private static String parseDataElement(Element element) {
		if (TagNames.Values.TRUE.equals(element.getAttributeValue(TagNames.Attributes.UNPRINTABALE))) {
			String hexValue = element.getAttributeValue(TagNames.Attributes.HEXVALUE);
			if (hexValue == null) {
				hexValue = "";
			}
			return StringHelper.decodeHex(hexValue);
		}

		return element.getText();
	}

    /**
     * Consume an XML message and update the specified QueryResults instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param resultsElement the XML element that contains the data
     * @return the updated instance.
     * @throws JDOMException 
     */
    private QueryResults consumeMsg(QueryResults object, Element resultsElement) throws JDOMException {
        // -----------------------
        // Process the element ...
        // -----------------------
        QueryResults results = object;
        if ( results == null ) {
            results = new QueryResults();
        }

        if ( resultsElement.getChild(TagNames.Elements.SELECT) == null ) {
            return results;
        }
        // -------------------------------
        // Read the SELECT elements
        // -------------------------------
        Element selectElement = resultsElement.getChild(TagNames.Elements.SELECT);
        Select select = new Select();
        select = consumeMsg(select, selectElement);

        List<SelectSymbol> listOfElementSymbols = select.getSymbols();
        Iterator<SelectSymbol> elementSymbolItr = listOfElementSymbols.iterator();
        Collection<ColumnInfo> collectionOfColumnInfos = new ArrayList<ColumnInfo>();
        while ( elementSymbolItr.hasNext() ) {
            ElementSymbol elementSymbol = (ElementSymbol) elementSymbolItr.next();
 //           Class elementType = elementSymbol.getType();
            String dataType = elementSymbol.getType();
  //          String dataType = DataTypeManager.getDataTypeName(elementType);
 //           ColumnInfo columnInfo = new ColumnInfo(elementSymbol.getName(), dataType, elementType);
            ColumnInfo columnInfo = new ColumnInfo(elementSymbol.getName(), dataType);
                       collectionOfColumnInfos.add(columnInfo);
        }
        // Save column info
        results.addFields(collectionOfColumnInfos);
        // -------------------------------
        // Read the TABLE of data
        // -------------------------------

        Element tableElement = resultsElement.getChild(TagNames.Elements.TABLE);
        List<Element> tableRows = tableElement.getChildren(TagNames.Elements.TABLE_ROW);
        if ( tableRows.size() > 0 ) {
            Iterator<Element> rowIter = tableRows.iterator();

            while ( rowIter.hasNext() ) {
                Element rowElement = rowIter.next();
                List<Element> cellElements = rowElement.getChildren(TagNames.Elements.TABLE_CELL);
                Iterator<Element> cellIter = cellElements.iterator();
                // Read cells of the table
                ArrayList<Object> row = new ArrayList<Object>();
                Object evalue = null;
                while ( cellIter.hasNext() ) {
                    Element cellElement = cellIter.next();
                    if ( cellElement.getTextTrim().equalsIgnoreCase(TagNames.Elements.NULL) ) {
                        row.add(null);
                    } else {
                        Element cellChildElement = cellElement.getChildren().get(0);
                        evalue = consumeMsg(cellChildElement);
                        row.add(evalue);
                    }
                }
                // Save row
                results.addRecord(row);
            }
        }
        return results;
    }

    /**
     * Consume an XML message and update the specified Select instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param selectElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Select consumeMsg(Select object, Element selectElement) throws JDOMException {

        Select select = (object != null) ? object : new Select();
        // --------------------------------
        // Read the DISTINCT attribute
        // --------------------------------

        String distinct = selectElement.getAttributeValue(TagNames.Attributes.DISTINCT);
        if ( distinct != null ) {
            if ( distinct.equalsIgnoreCase("true") ) { //$NON-NLS-1$
                select.setDistinct(true);
            }
        }

        // --------------------------------
        // Read the STAR attribute
        // --------------------------------

        String star = selectElement.getAttributeValue(TagNames.Attributes.STAR);
        if ( star != null ) {
            if ( star.equalsIgnoreCase("true") ) { //$NON-NLS-1$
                if ( selectElement.getChildren() != null ) {
                    throw new JDOMException("No children expected when star is chosen."); //$NON-NLS-1$
                }
                return select;
            }
        }

        // --------------------------------
        // Read the IDENTIFIER elements ...
        // --------------------------------
        List<Element> idents = selectElement.getChildren();
        Iterator<Element> identIter = idents.iterator();
        while ( identIter.hasNext() ) {
            Element dataElement = identIter.next();
            Attribute dataType = dataElement.getAttribute(TagNames.Attributes.TYPE);
            // add the dataType of the element to the list containing dataTypes
            ElementSymbol nodeID = new ElementSymbol(dataElement.getText());
 //           Class nodeType = (Class) TagNames.TYPE_MAP.get(dataType.getValue());
            nodeID.setType(dataType.getValue());
//            if (nodeType == null)  {
//            	ClientPlugin.LOGGER.error("Unknown class for type \"" + dataType.getValue() + ", using " + dataType.getValue());
//   //             throw new JDOMException("Unknown class for type \"" + dataType.getValue() + "\"."); //$NON-NLS-1$ //$NON-NLS-2$
//            	  
//            	try {
//					nodeID.setType(Class.forName(dataType.getValue()));
//				} catch (ClassNotFoundException e) {
//					throw new FrameworkRuntimeException(e);
//				}
//            } else {
//            	nodeID.setType(nodeType);
//            }
            select.addSymbol(nodeID);
        }

        return select;
    }


    /**
     * Produce a JDOM Element for the instance of any Object.
     * <br>
     * @param cellElement the XML element that is to produce the XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing XML.
     */
    private Object consumeMsg(Element cellElement) throws JDOMException {

        Object cellObject = null;;
        String cellName = cellElement.getName();

        if ( cellName.equalsIgnoreCase(TagNames.Elements.BOOLEAN) ) {
            cellObject = consumeMsg((Boolean) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.STRING) ) {
            cellObject = consumeMsg((String) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.CHAR) ) {
            cellObject = consumeMsg((Character) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.BYTE) ) {
            cellObject = consumeMsg((Byte) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.DOUBLE) ) {
            cellObject = consumeMsg((Double) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.DATE) ) {
            cellObject = consumeMsg((java.sql.Date) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.TIME) ) {
            cellObject = consumeMsg((Time) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.TIMESTAMP) ) {
            cellObject = consumeMsg((Timestamp) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.FLOAT) ) {
            cellObject = consumeMsg((Float) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.BIGDECIMAL) ) {
            cellObject = consumeMsg((BigDecimal) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.BIGINTEGER) ) {
            cellObject = consumeMsg((BigInteger) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.INTEGER) ) {
            cellObject = consumeMsg((Integer) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.LONG) ) {
            cellObject = consumeMsg((Long) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.SHORT) ) {
            cellObject = consumeMsg((Short) cellObject, cellElement);
        } else if ( cellName.equalsIgnoreCase(TagNames.Elements.OBJECT) ) {
            cellObject = consumeMsg((String) cellObject, cellElement);
        } else {
        	cellObject = consumeMsg(cellObject, cellElement);
        }

        return cellObject;
    }

    /**
     * Consume an XML message and update the specified Boolean instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Boolean object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        boolean result = false;
        String value = cellElement.getTextTrim();
        if ( value.equalsIgnoreCase(TagNames.Values.TRUE) ) {
            result = true;
        } else if ( value.equalsIgnoreCase(TagNames.Values.FALSE) ) {
            result = false;
        } else {
            throw new JDOMException("Invalid value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: \"" + value + "\" must be either \"" + //$NON-NLS-1$ //$NON-NLS-2$
                                    TagNames.Values.TRUE + "\" or \"" + //$NON-NLS-1$
                                    TagNames.Values.FALSE + "\""); //$NON-NLS-1$
        }

        return new Boolean(result);
    }

    /**
     * Consume an XML message and update the specified java.sql.Date instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(java.sql.Date object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        java.sql.Date result;
        try {
            result = java.sql.Date.valueOf(cellElement.getTextTrim());
        } catch ( Exception e ) {
            throw new JDOMException("Invalid input format ", e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified Time instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Time object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Time result;
        try {
            result = Time.valueOf(cellElement.getTextTrim());
        } catch ( Exception e ) {
            throw new JDOMException("Invalid input format ", e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified Timestamp instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Timestamp object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Timestamp result;
        try {
            result = Timestamp.valueOf(cellElement.getTextTrim());
        } catch ( Exception e ) {
            throw new JDOMException("Invalid input format ", e); //$NON-NLS-1$
        }

        return result;
    }

    /**
     * Consume an XML message and update the specified Double instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Double object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        String strElement = cellElement.getTextTrim();
        Double result;

        if ( strElement.equals("NaN") ) { //$NON-NLS-1$
            result = new Double(Double.NaN);
        } else if ( strElement.equals("-Infinity") ) { //$NON-NLS-1$
            result = new Double(Double.NEGATIVE_INFINITY);
        } else if ( strElement.equals("Infinity") ) { //$NON-NLS-1$
            result = new Double(Double.POSITIVE_INFINITY);
        } else {
            try {
                result = Double.valueOf(strElement);
            } catch ( NumberFormatException e ) {
                throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                        " element: " + strElement, e); //$NON-NLS-1$
            }
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified Float instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Float object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        String strElement = cellElement.getTextTrim();
        Float result;

        if ( strElement.equals("NaN") ) { //$NON-NLS-1$
            result = new Float(Float.NaN);
        } else if ( strElement.equals("-Infinity") ) { //$NON-NLS-1$
            result = new Float(Float.NEGATIVE_INFINITY);
        } else if ( strElement.equals("Infinity") ) { //$NON-NLS-1$
            result = new Float(Float.POSITIVE_INFINITY);
        } else {
            try {
                result = Float.valueOf(strElement);
            } catch ( NumberFormatException e ) {
                throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                        " element: " + strElement, e); //$NON-NLS-1$
            }
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified BigDecimal instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(BigDecimal object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        BigDecimal result;
        try {
            result = new BigDecimal(cellElement.getTextTrim());
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified BigInteger instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(BigInteger object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        BigInteger result;
        try {
            result = new BigInteger(cellElement.getTextTrim());
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified String instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(String object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------

    	return parseDataElement(cellElement);
    }

    /**
     * Consume an XML message and update the specified Character instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Character object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Character result;
        try {
			String content = parseDataElement(cellElement);
			if (content.length() == 0) {
				return null;
			}
			result = new Character(content.charAt(0));
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getText(), e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified Byte instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Byte object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Byte result;
        try {
            result = new Byte(cellElement.getTextTrim());
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified Integer instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Integer object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Integer result;
        try {
            result = Integer.valueOf(cellElement.getTextTrim());
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-2$ //$NON-NLS-1$ //$NON-NLS-1$ //$NON-NLS-1$ //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Consume an XML message and update the specified Long instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Long object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Long result;
        try {
            result = Long.valueOf(cellElement.getTextTrim());
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-2$ //$NON-NLS-1$ //$NON-NLS-1$ //$NON-NLS-1$ //$NON-NLS-1$
        }
        return result;
    }
    
    
    /**
     * Consume an XML message and update the specified Byte instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
	private Object consumeMsg(Object object, Element cellElement) throws JDOMException {

//        // -----------------------
//        // Process the element ...
//        // -----------------------
//        Byte result;
//        try {
//            result = new Byte(cellElement.getTextTrim());
//        } catch ( NumberFormatException e ) {
//            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
//                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-1$
//        }
//        return result;
        
    	return parseDataElement(cellElement);
        // ----------------------
        // Create the Object element ...
        // ----------------------
//        Element objectElement = new Element(TagNames.Elements.OBJECT);
//        
//        String result = null;
//        if (object instanceof Blob || object instanceof Clob || object instanceof SQLXML) {
//       	 
//        	if (object instanceof Clob){
//        		Clob c = (Clob)object;
//        		try {
//        			result = ObjectConverterUtil.convertToString(c.getAsciiStream());
//					
//				} catch (Throwable e) {
//					throw new SQLException(e);
//				}
//        	} else if (object instanceof Blob){
//            		Blob b = (Blob)object;
//            		try {
//            			result = ObjectConverterUtil.convertToString(b.getBinaryStream());
//						
//					} catch (Throwable e) {
//						throw new SQLException(e);
//					}
//            } else if (object instanceof SQLXML){
//            	SQLXML s = (SQLXML)object;
//        		try {
//        			result = ObjectConverterUtil.convertToString(s.getBinaryStream());
//					
//				} catch (Throwable e) {
//					throw new SQLException(e);
//				}
//            } 
//        } else {
//        	result = object.toString();
//        }
//        
// //       System.out.println("ProductObject (before encoding): " + object.toString() );
// //       try {
//            objectElement.setText(result);
//            	//	URLEncoder.encode(object.toString(), "UTF-8"));
// //       } catch (UnsupportedEncodingException e) {
//            // UTF-8 is supported natively by all jvms
// //       }
////        System.out.println("ProductObject (after encoding): " + objectElement.getText() );
//
//        
//        if ( parent != null ) {
//            objectElement = parent.addContent(objectElement);
//        }
//
//        return objectElement;

    }

    /**
     * Consume an XML message and update the specified Long instance.
     * <br>
     * @param object the instance that is to be updated with the XML message data.
     * @param cellElement the XML element that contains the data
     * @return the updated instance.
     * @exception JDOMException if there is an error consuming the message.
     */
    private Object consumeMsg(Short object, Element cellElement) throws JDOMException {

        // -----------------------
        // Process the element ...
        // -----------------------
        Short result;
        try {
            result = Short.valueOf(cellElement.getTextTrim());
        } catch ( NumberFormatException e ) {
            throw new JDOMException("Unable to parse the value for " + cellElement.getName() + //$NON-NLS-1$
                                    " element: " + cellElement.getTextTrim(), e); //$NON-NLS-2$ //$NON-NLS-1$ //$NON-NLS-1$ //$NON-NLS-1$ //$NON-NLS-1$
        }
        return result;
    }

    /*********************************************************************************************
     *********************************************************************************************
     PRODUCE METHODS
     *********************************************************************************************
     ********************************************************************************************/

    /**
     * Produce a JDOM Element for an instance of a JDBC ResultSet object.
     * <br>
     * @param object for which the JDOM Element is to be produced.
     * @return the JDOM element of the ResultSet object that was converted to XML.
     * @exception JDOMException if there is an error producing XML.
     * @exception JDOMException if there is an error producing XML.
     * @exception SQLException if there is an error walking through the ResultSet object.
     */
    public Element produceResults(ResultSet object) throws JDOMException, SQLException {

        // When no begin and end
        return produceResults(object, START_ROW, Integer.MAX_VALUE);
    }

    /**
     * Produce a JDOM Element for an instance of Results object.
     * <br>
     * @param object for which the JDOM Element is to be produced.
     * @param beginRow The starting row from which the results are to be converted to XML.
     * @param endRow The row until which the results are to be converted to XML.
     * @return the JDOM element of the results object that was converted to XML.
     * @exception JDOMException if there is an error producing XML.
     * @exception SQLException if there is an error walking through the ResultSet object.
     */
    private Element produceResults(ResultSet object, int beginRow, int endRow)
            throws JDOMException, SQLException {

    	if (object.isClosed()) {
            throw new SQLException(
            "ResultSet is closed at this point, unable to product results"); //$NON-NLS-1$
    		
    	}
    	
        if ( beginRow < START_ROW ) {
            throw new IllegalArgumentException(
                    "The starting row cannot be less than 1."); //$NON-NLS-1$
        } else if ( beginRow > endRow ) {
            throw new IllegalArgumentException(
                    "The starting row cannot be less than the ending row."); //$NON-NLS-1$
        }

        int currentRow = object.getRow() + 1;

        if ( beginRow > currentRow ) {
            while ( !object.isLast() && currentRow != beginRow ) {
                object.next();
                currentRow++;
            }

        } else if ( beginRow < currentRow ) {
            while ( !object.isFirst() && currentRow != beginRow ) {
                object.previous();
                currentRow--;
            }
        }

        return produceMsg(object, endRow);
    }

    /**
     * Produce a JDOM Element for an instance of a JDBC ResultSet object.
     * <br>
     * @param object for which the JDOM Element is to be produced.
     * @param endRow The row until which the results are to be converted to XML.
     * @return the JDOM element of the results object that was converted to XML.
     * @exception JDOMException if there is an error producing XML.
     * @exception SQLException if there is an error walking through the ResultSet object.
     */
    private Element produceMsg(ResultSet object, int endRow) throws JDOMException, SQLException {

        // -----------------------------------
        // Create the QueryResults element ...
        // -----------------------------------
        Element resultsElement = new Element(TagNames.Elements.QUERY_RESULTS);

        // -----------------------------------
        // Add the Select (header) element ...
        // -----------------------------------
        try {
            ResultSetMetaData rmdata = object.getMetaData();
            List<SelectSymbol> identList = new ArrayList<SelectSymbol>(rmdata.getColumnCount());
            for ( int i = 1; i <= rmdata.getColumnCount(); i++ ) {
                identList.add(new ElementSymbol(rmdata.getColumnName(i)));
            }
            Select select = new Select(identList);
            resultsElement = produceMsg(select, rmdata, resultsElement);

            // -------------------------
            // Add the Table element ...
            // -------------------------
            resultsElement.addContent(new Element(TagNames.Elements.TABLE));
            Element tableElement = resultsElement.getChild(TagNames.Elements.TABLE);
            int rowCount = 0;
            int colCount = rmdata.getColumnCount();

            while ( object.next() && (object.getRow() <= endRow) ) {

                // -------------------------
                // Add the ROW element ...
                // -------------------------
                Element rowElement = new Element(TagNames.Elements.TABLE_ROW);

                for ( int i = 1; i <= colCount; i++ ) {
                    // -------------------------
                    // Add the Cell element ...
                    // -------------------------
                    Element cellElement = new Element(TagNames.Elements.TABLE_CELL);
                    Object cellValue = object.getObject(i);
                    if ( cellValue != null ) {
                        cellElement = produceMsg(cellValue, cellElement);
                    } else {
                        cellElement = cellElement.addContent(TagNames.Elements.NULL);
                    }
                    rowElement.addContent(cellElement);
                }
                tableElement.addContent(rowElement);
                rowCount++;
            }
            Attribute rowCountAttribute = new Attribute(TagNames.Attributes.TABLE_ROW_COUNT,
                                                        Integer.toString(rowCount));
            Attribute columnCountAttribute = new Attribute(TagNames.Attributes.TABLE_COLUMN_COUNT,
                                                           Integer.toString(colCount));
            tableElement.setAttribute(rowCountAttribute);
            tableElement.setAttribute(columnCountAttribute);
        } catch ( SQLException e ) {
            // error while reading results
            throw(e);
        }

        return resultsElement;
    }

    /**
     * Produce a JDOM Element for an instance of a JDBC ResultSet object.
     * <br>
     * @param object for which the JDOM Element is to be produced.
     * @param resultsElement 
     * @return the JDOM element of the results object that was converted to XML.
     * @exception JDOMException if there is an error producing XML.
     * @exception SQLException if there is an error walking through the ResultSet object.
     */
    public Element produceMsg(ResultSet object, Element resultsElement) throws JDOMException, SQLException {

        // -----------------------------------
        // Add the Select (header) element ...
        // -----------------------------------
        try {
            ResultSetMetaData rmdata = object.getMetaData();
            List<SelectSymbol> identList = new ArrayList<SelectSymbol>(rmdata.getColumnCount());
            for ( int i = 1; i <= rmdata.getColumnCount(); i++ ) {
                identList.add(new ElementSymbol(rmdata.getColumnName(i)));
            }
            Select select = new Select(identList);
            resultsElement = produceMsg(select, rmdata, resultsElement);

            // -------------------------
            // Add the Table element ...
            // -------------------------
            resultsElement.addContent(new Element(TagNames.Elements.TABLE));
            Element tableElement = resultsElement.getChild(TagNames.Elements.TABLE);
            int rowCount = 0;
            int colCount = rmdata.getColumnCount();

            while ( object.next() ) {

                // -------------------------
                // Add the ROW element ...
                // -------------------------
                Element rowElement = new Element(TagNames.Elements.TABLE_ROW);

                for ( int i = 1; i <= colCount; i++ ) {
                    // -------------------------
                    // Add the Cell element ...
                    // -------------------------
                    Element cellElement = new Element(TagNames.Elements.TABLE_CELL);
                    Object cellValue = object.getObject(i);
                    if ( cellValue != null ) {
                        cellElement = produceMsg(cellValue, cellElement);
                    } else {
                        cellElement = cellElement.addContent(TagNames.Elements.NULL);
                    }
                    rowElement.addContent(cellElement);
                }
                tableElement.addContent(rowElement);
                rowCount++;
            }
            Attribute rowCountAttribute = new Attribute(TagNames.Attributes.TABLE_ROW_COUNT,
                                                        Integer.toString(rowCount));
            Attribute columnCountAttribute = new Attribute(TagNames.Attributes.TABLE_COLUMN_COUNT,
                                                           Integer.toString(colCount));
            tableElement.setAttribute(rowCountAttribute);
            tableElement.setAttribute(columnCountAttribute);
        } catch ( SQLException e ) {
            // error while reading results
            throw(e);
        }

        return resultsElement;
    }

    /**
     * Produce a JDOM Element for the instance of any Object.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing XML.
     * @throws SQLException 
     */
    public Element produceMsg(Object object, Element parent) throws JDOMException, SQLException {
        if ( object == null ) {
            throw new IllegalArgumentException("Null object reference."); //$NON-NLS-1$
        }
        Element element = null;

        if ( object instanceof Boolean ) {
            element = produceMsg((Boolean) object, parent);
        } else if ( object instanceof String ) {
            element = produceMsg((String) object, parent);
        } else if ( object instanceof Character ) {
            element = produceMsg((Character) object, parent);
        } else if ( object instanceof Byte ) {
            element = produceMsg((Byte) object, parent);
        } else if ( object instanceof Double ) {
            element = produceMsg((Double) object, parent);
        } else if ( object instanceof java.sql.Date ) {
            element = produceMsg((java.sql.Date) object, parent);
        } else if ( object instanceof Time ) {
            element = produceMsg((Time) object, parent);
        } else if ( object instanceof Timestamp ) {
            element = produceMsg((Timestamp) object, parent);
        } else if ( object instanceof Float ) {
            element = produceMsg((Float) object, parent);
        } else if ( object instanceof BigDecimal ) {
            element = produceMsg((BigDecimal) object, parent);
        } else if ( object instanceof BigInteger ) {
            element = produceMsg((BigInteger) object, parent);
        } else if ( object instanceof Integer ) {
            element = produceMsg((Integer) object, parent);
        } else if ( object instanceof Long ) {
            element = produceMsg((Long) object, parent);
        } else if ( object instanceof Short ) {
            element = produceMsg((Short) object, parent);
        } else if ( object instanceof Throwable ) {
            element = produceMsg((Throwable) object, parent);
        } else {
            element = produceObject(object, parent);
        }

        return element;
    }

    /**
     * new ----
     * @param select
     * @param rmdata
     * @param parent
     * @return Element
     * @throws JDOMException
     */
    private Element produceMsg(Select select, ResultSetMetaData rmdata, Element parent)
            throws JDOMException {

        // -----------------------------------
        // Create the Select element ...
        // -----------------------------------

        Element selectElement = new Element(TagNames.Elements.SELECT);

        // ---------------------------------
        // Create the DISTINCT attribute ...
        // ---------------------------------
        boolean distinct = select.isDistinct();
        if ( distinct ) {
            Attribute distinctAttribute = new Attribute(TagNames.Attributes.DISTINCT, "true"); //$NON-NLS-1$
            selectElement.setAttribute(distinctAttribute);
        } // else default is false so no need

        // ----------------------------------
        // Create the STAR attribute ...
        // ----------------------------------
        if ( select.isStar() ) {
            Attribute starAttribute = new Attribute(TagNames.Attributes.STAR, "true"); //$NON-NLS-1$
            selectElement.setAttribute(starAttribute);
        }

        // --------------------------------
        // Create the DATANODE elements ...
        // --------------------------------
        int col = 0;
        Iterator<SelectSymbol> iter = select.getSymbols().iterator();
        while ( iter.hasNext() ) {
            Element dataElement = new Element(TagNames.Elements.DATA_ELEMENT);
            ElementSymbol symbol = (ElementSymbol) iter.next();
            String elementName = symbol.getName();
            Attribute dataType = null;
            try {
                dataType = new Attribute(TagNames.Attributes.TYPE, rmdata.getColumnTypeName(++col));
            } catch ( SQLException e ) {
                //
            }
            dataElement.setAttribute(dataType);
            dataElement.setText(elementName);
            selectElement.addContent(dataElement);
        }
        if ( parent != null ) {
            selectElement = parent.addContent(selectElement);
        }

        return selectElement;
    }

    /**
     * Produce an XML message for an instance of the Object.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     * @throws SQLException 
     */
    private Element produceObject(Object object, Element parent) throws JDOMException, SQLException { //TODO - write results

         // ----------------------
        // Create the Object element ...
        // ----------------------
        Element objectElement = new Element(TagNames.Elements.OBJECT);
        
        String result = null;
        if (object instanceof Blob || object instanceof Clob || object instanceof SQLXML) {
       	 
        	if (object instanceof Clob){
        		Clob c = (Clob)object;
        		try {
        			result = ObjectConverterUtil.convertToString(c.getAsciiStream());
					
				} catch (Throwable e) {
					throw new SQLException(e);
				}
        	} else if (object instanceof Blob){        		
            		Blob b = (Blob)object;
            		try {
            			byte[] ba = ObjectConverterUtil.convertToByteArray(b.getBinaryStream());
            			
            			result = String.valueOf(ba.length);
            			
					} catch (Throwable e) {
						throw new SQLException(e);
					}
            } else if (object instanceof SQLXML){

            	SQLXML s = (SQLXML)object;
        		try {
        			result = ObjectConverterUtil.convertToString(s.getBinaryStream());
					
				} catch (Throwable e) {
					throw new SQLException(e);
				}
            } 
        } else {
        	result = object.toString();
        }
        
        fillDataElement(result, objectElement);

    
        if ( parent != null ) {
            objectElement = parent.addContent(objectElement);
        }

        return objectElement;
    }

    /**
     * Produce an XML message for an instance of the String.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(String object, Element parent) throws JDOMException {
        // ----------------------
        // Create the String element ...
        // ----------------------
        Element stringElement = new Element(TagNames.Elements.STRING);
        fillDataElement(object, stringElement);
        if ( parent != null ) {
            stringElement = parent.addContent(stringElement);
        }

        return stringElement;
    }

    /**
     * Produce an XML message for an instance of the Character.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Character object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Character element ...
        // ----------------------
        Element charElement = new Element(TagNames.Elements.CHAR);

		String content = object.toString();
		fillDataElement(content, charElement);

        if ( parent != null ) {
            charElement = parent.addContent(charElement);
        }


        return charElement;
    }

    /**
     * Produce an XML message for an instance of the Byte.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Byte object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Byte element ...
        // ----------------------
        Element byteElement = new Element(TagNames.Elements.BYTE);
        byteElement.setText(object.toString());
        if ( parent != null ) {
            byteElement = parent.addContent(byteElement);
        }

        return byteElement;
    }

    /**
     * Produce an XML message for an instance of the Boolean.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Boolean object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Boolean element ...
        // ----------------------
        Element booleanElement = new Element(TagNames.Elements.BOOLEAN);

        if ( object.booleanValue() == true ) {
            booleanElement.setText(TagNames.Values.TRUE);
        } else {
            booleanElement.setText(TagNames.Values.FALSE);
        }

        if ( parent != null ) {
            booleanElement = parent.addContent(booleanElement);
        }

        return booleanElement;
    }

    /**
     * Produce an XML message for an instance of the Float.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Float object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Float element ...
        // ----------------------
        Element floatElement = new Element(TagNames.Elements.FLOAT);
        floatElement.setText(object.toString());
        if ( parent != null ) {
            floatElement = parent.addContent(floatElement);
        }

        return floatElement;
    }

    /**
     * Produce an XML message for an instance of the Double.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Double object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Double element ...
        // ----------------------
        Element doubleElement = new Element(TagNames.Elements.DOUBLE);
        doubleElement.setText(object.toString());
        if ( parent != null ) {
            doubleElement = parent.addContent(doubleElement);
        }

        return doubleElement;
    }

    /**
     * Produce an XML message for an instance of the BigDecimal.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(BigDecimal object, Element parent) throws JDOMException {

        // ----------------------
        // Create the BigDecimal element ...
        // ----------------------
        Element bigDecimalElement = new Element(TagNames.Elements.BIGDECIMAL);
        bigDecimalElement.setText(object.toString());
        if ( parent != null ) {
            bigDecimalElement = parent.addContent(bigDecimalElement);
        }

        return bigDecimalElement;
    }

    /**
     * Produce an XML message for an instance of the BigInteger.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(BigInteger object, Element parent) throws JDOMException {

        // ----------------------
        // Create the BigInteger element ...
        // ----------------------
        Element bigIntegerElement = new Element(TagNames.Elements.BIGINTEGER);
        bigIntegerElement.setText(object.toString());
        if ( parent != null ) {
            bigIntegerElement = parent.addContent(bigIntegerElement);
        }

        return bigIntegerElement;
    }

    /**
     * Produce an XML message for an instance of the java.sql.Date.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(java.sql.Date object, Element parent) throws JDOMException {

        // ----------------------
        // Create the java.sql.Date element ...
        // ----------------------
        Element sqldateElement = new Element(TagNames.Elements.DATE);
        sqldateElement.setText(object.toString());
        if ( parent != null ) {
            sqldateElement = parent.addContent(sqldateElement);
        }

        return sqldateElement;
    }

    /**
     * Produce an XML message for an instance of the Time.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Time object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Time element ...
        // ----------------------
        Element timeElement = new Element(TagNames.Elements.TIME);
        timeElement.setText(object.toString());
        if ( parent != null ) {
            timeElement = parent.addContent(timeElement);
        }

        return timeElement;
    }

    /**
     * Produce an XML message for an instance of the Timestamp.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Timestamp object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Timestamp element ...
        // ----------------------
        Element timestampElement = new Element(TagNames.Elements.TIMESTAMP);
        timestampElement.setText(object.toString());
        if ( parent != null ) {
            timestampElement = parent.addContent(timestampElement);
        }

        return timestampElement;
    }

    /**
     * Produce an XML message for an instance of the Integer.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Integer object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Integer element ...
        // ----------------------
        Element integerElement = new Element(TagNames.Elements.INTEGER);
        integerElement.setText(object.toString());
        if ( parent != null ) {
            integerElement = parent.addContent(integerElement);
        }

        return integerElement;
    }

    /**
     * Produce an XML message for an instance of the Long.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Long object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Long element ...
        // ----------------------
        Element longElement = new Element(TagNames.Elements.LONG);
        longElement.setText(object.toString());
        if ( parent != null ) {
            longElement = parent.addContent(longElement);
        }

        return longElement;
    }

    /**
     * Produce an XML message for an instance of the Short.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Short object, Element parent) throws JDOMException {

        // ----------------------
        // Create the Long element ...
        // ----------------------
        Element shortElement = new Element(TagNames.Elements.SHORT);
        shortElement.setText(object.toString());
        if ( parent != null ) {
            shortElement = parent.addContent(shortElement);
        }

        return shortElement;
    }
    


    /**
     * Produce an XML message for an instance of the SQLException.
     * <br>
     * @param object the instance for which the message is to be produced.
     * @param parent the XML element that is to be the parent of the produced XML message.
     * @return the root element of the XML segment that was produced.
     * @exception JDOMException if there is an error producing the message.
     */
    private Element produceMsg(Throwable object, Element parent) throws JDOMException {

        Throwable exception = object;
        Element exceptionElement = null;

        // --------------------------------
        // Create the Exception element ...
        // --------------------------------
        exceptionElement = new Element(TagNames.Elements.EXCEPTION);

        // ---------------------------------
        // Add the ExceptionType element ...
        // ---------------------------------
        String className = exception.getClass().getName();
        int index = className.lastIndexOf('.');
        if ( index != -1 && (++index) < className.length() ) {
            className = className.substring(index);
        }
        Element typeElement = new Element(TagNames.Elements.EXCEPTION_TYPE);
        typeElement.setText(className);
        exceptionElement.addContent(typeElement);

        // ---------------------------
        // Add the Message element ...
        // ---------------------------
        Element messageElement = new Element(TagNames.Elements.MESSAGE);
        
        messageElement.setText(StringUtils.remove(ExceptionUtil.getExceptionMessage(exception), '\r'));
         
        exceptionElement.addContent(messageElement);

        // -------------------------
        // Add the Class element ...
        // -------------------------
        Element classElement = new Element(TagNames.Elements.CLASS);
        classElement.setText(exception.getClass().getName());
        exceptionElement.addContent(classElement);

		// ------------------------------
		// Add the StackTrace element ...
		// ------------------------------
		StringWriter sw = new StringWriter();
		object.printStackTrace(new PrintWriter(sw));

		Element stackTraceElement = new Element(TagNames.Elements.STACK_TRACE);
		stackTraceElement.setText(sw.toString());
		exceptionElement.addContent(stackTraceElement);

        if ( parent != null ) {
            exceptionElement = parent.addContent(exceptionElement);
        }

        return exceptionElement;
    }
    
}
