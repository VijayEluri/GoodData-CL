package com.gooddata.connector;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.gooddata.connector.AbstractConnector;
import org.gooddata.connector.Connector;
import org.gooddata.connector.backend.ConnectorBackend;

import com.gooddata.exception.ModelException;
import com.gooddata.exception.ProcessingException;
import com.gooddata.modeling.model.SourceColumn;
import com.gooddata.modeling.model.SourceSchema;
import com.gooddata.processor.CliParams;
import com.gooddata.processor.Command;
import com.gooddata.processor.ProcessingContext;
import com.gooddata.util.FileUtil;
import com.gooddata.util.StringUtil;

/**
 * GoodData CSV Connector
 *
 * @author zd <zd@gooddata.com>
 * @version 1.0
 */
public class CsvConnector extends AbstractConnector implements Connector {

    
    // data file
    private File dataFile;

    private boolean hasHeader;

    /**
     * Creates GoodData CSV connector
     * @param backend connector backend
     */
    protected CsvConnector(ConnectorBackend backend) {
        super(backend);
    }

    /**
     * Create a new GoodData CSV connector. This constructor creates the connector from a config file
     * @param backend connector backend
     * @return new CSV Connector
     */
    public static CsvConnector createConnector(ConnectorBackend backend) {
        return new CsvConnector(backend);    
    }

    /**
     * Saves a template of the config file
     * @param configFileName the new config file name
     * @param dataFileName the data file
     * @throws IOException in case of an IO error
     */
    public static void saveConfigTemplate(String configFileName, String dataFileName) throws IOException {
    	saveConfigTemplate(configFileName, dataFileName, null, null);
    }

    /**
     * Saves a template of the config file
     * @param configFileName the new config file name
     * @param dataFileName the data file
     * @param defaultLdmType default LDM type
     * @param folder default folder
     * @throws IOException in case of an IO issue
     */
    public static void saveConfigTemplate(String configFileName, String dataFileName, String defaultLdmType, String folder) throws IOException {
        File dataFile = new File(dataFileName);
        String name = dataFile.getName().split("\\.")[0];
        String[] headers = FileUtil.getCsvHeader(dataFile);
        int i = 0;
        final SourceSchema s;
        File configFile = new File(configFileName);
        if (configFile.exists()) {
        	s = SourceSchema.createSchema(configFile);
        } else {
        	s = SourceSchema.createSchema(name);
        }
        final int knownColumns = s.getColumns().size();
        NameTransformer idGen = new NameTransformer(new NameTransformerCallback() {
        	public String transform(String str) {
        		return StringUtil.csvHeaderToIdentifier(str);
        	}
        });
        NameTransformer titleGen = new NameTransformer(new NameTransformerCallback() {
        	public String transform(String str) {
        		return StringUtil.csvHeaderToTitle(str);
        	}
        });
        
        for(int j = knownColumns; j < headers.length; j++) {
        	final String header = headers[j];
            final SourceColumn sc;
            final String identifier = idGen.transform(header);
            final String title = titleGen.transform(header);
            if (defaultLdmType != null) {
            	sc = new SourceColumn(identifier, defaultLdmType, title, folder);
            } else {
	            switch (i %3) {
	                case 0:
	                    sc = new SourceColumn(identifier, SourceColumn.LDM_TYPE_ATTRIBUTE, title, "folder");
	                    break;
	                case 1:
	                    sc = new SourceColumn(identifier, SourceColumn.LDM_TYPE_FACT, title, "folder");
	                    break;
	                case 2:
	                    sc = new SourceColumn(identifier, SourceColumn.LDM_TYPE_LABEL, title, "folder", "existing-attribute-name");
	                    break;
	                default:
	                	throw new AssertionError("i % 3 outside {0, 1, 2} - this cannot happen");
	            }
            }
            s.addColumn(sc);
            i++;
        }
        s.writeConfig(new File(configFileName));
    }

    /**
     * Data CSV file getter
     * @return the data CSV file
     */
    public File getDataFile() {
        return dataFile;
    }

    /**
     * Data CSV file setter
     * @param dataFile the data CSV file
     */
    public void setDataFile(File dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * Extracts the source data CSV to the Derby database where it is going to be transformed
     * @throws ModelException in case of PDM schema issues
     */
    public void extract() throws ModelException, IOException {
        if(getHasHeader()) {
            File tmp = FileUtil.stripCsvHeader(getDataFile());
            getConnectorBackend().extract(tmp);
            tmp.delete();
        }
    }

    /**
     * hasHeader getter
     * @return hasHeader flag
     */
    public boolean getHasHeader() {
        return hasHeader;
    }

    /**
     * hasHeader setter
     * @param hasHeader flag value
     */
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    /**
     * {@inheritDoc}
     */
    public boolean processCommand(Command c, CliParams cli, ProcessingContext ctx) throws ProcessingException {
        try {
            if(c.match("GenerateCsvConfig")) {
                generateCsvConfig(c, cli, ctx);
            }
            else if(c.match("LoadCsv")) {
                loadCsv(c, cli, ctx);
            }
            else
                return super.processCommand(c, cli, ctx);
        }
        catch (IOException e) {
            throw new ProcessingException(e);
        }
        return true;
    }

    /**
     * Loads new CSV file command processor
     * @param c command
     * @param p command line arguments
     * @param ctx current processing context
     * @throws IOException in case of IO issues
     */
    private void loadCsv(Command c, CliParams p, ProcessingContext ctx) throws IOException {
        String configFile = c.getParamMandatory("configFile");
        String csvDataFile = c.getParamMandatory("csvDataFile");
        String hdr = c.getParamMandatory("header");
        File conf = FileUtil.getFile(configFile);
        File csvf = FileUtil.getFile(csvDataFile);
        boolean hasHeader = false;
        if(hdr.equalsIgnoreCase("true"))
            hasHeader = true;
        initSchema(conf.getAbsolutePath());
        setDataFile(new File(csvf.getAbsolutePath()));
        setHasHeader(hasHeader);
        // sets the current connector
        ctx.setConnector(this);
        setProjectId(ctx);
    }

    /**
     * Generate new config file from CSV command processor
     * @param c command
     * @param p command line arguments
     * @param ctx current processing context
     * @throws IOException in case of IO issues
     */
    private void generateCsvConfig(Command c, CliParams p, ProcessingContext ctx) throws IOException {
        String configFile = c.getParamMandatory("configFile");
        String csvHeaderFile = c.getParamMandatory("csvHeaderFile");
        File cf = new File(configFile);
        File csvf = FileUtil.getFile(csvHeaderFile);
        CsvConnector.saveConfigTemplate(configFile, csvHeaderFile);
    }
    
    private static class NameTransformer {
    	private final NameTransformerCallback cb;
    	private final Set<String> seen = new HashSet<String>();
    	
    	public NameTransformer(NameTransformerCallback cb) {
    		this.cb = cb;
    	}
    	
    	public String transform(String str) {
    		int index = 0;
    		String transformed = cb.transform(str);
    		while (true) {
    			String result = transformed;
    			if (index > 0) {
    				result += " " + index;
    			}
    			index++;
    			if (!seen.contains(result)) {
    				seen.add(result);
    				return result;
    			}
    		}
    	}
    }
    
    private interface NameTransformerCallback {
    	public String transform(String str);
    }
}