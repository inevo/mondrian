package mondrian.geo.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import mondrian.olap.Connection;
import mondrian.olap.DriverManager;
import mondrian.olap.Query;
import mondrian.olap.Result;

public class GeoMondrianTest {

    /**
     * @param args
     */
    public static void main(String[] args) {

        java.io.PrintStream cout = System.out;

        //      java.io.BufferedWriter bw = new BufferedWriter( new FileWriter("cubeout1.txt") );
        //      java.io.PrintWriter cout = new PrintWriter(bw);        

        Properties props = new Properties();
        try {
            props.load(new FileInputStream(new File("geomondrian/GeoMondrianTest.properties")));
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        


        String postgisConnectString = "jdbc:postgresql_postGIS://localhost/geofoodmart?user=mondrian&password=mondrian";
        String postgisSchema = "geomondrian/FoodMart.xml";

        
        String connectString = props.getProperty("connectString", postgisConnectString);
        String schemaFile = props.getProperty("schemaFile", postgisSchema);

        String mondrianConnString = "Provider=mondrian;" +
        "Jdbc=" + connectString + ";" +
        "JdbcDrivers=org.postgis.DriverWrapper;" +
        "Catalog=file:"+ schemaFile + ";";

        String mdxQueryFile = "geomondrian/query.mdx";
        
        Connection connection = DriverManager.getConnection(
                mondrianConnString, null
        );

        final String qryString =
            "SELECT {[Measures].[Population]} on columns, " +
            "{[Unite geographique].[Province].members} on rows " +
            "FROM [Recensements] " +
            "WHERE [Temps].[Rencensement 2001 (2001-2003)].[2001]";

        String mdxQuery = null;
        
        try {
            StringBuffer sb = new StringBuffer();
            BufferedReader br = new BufferedReader(new FileReader(new File(mdxQueryFile)));
            String line;
            while ( ( line = br.readLine()) != null ) {
                sb.append(line);
                sb.append(System.getProperty("line.separator"));
            }
            mdxQuery = sb.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        Query query = connection.parseQuery(mdxQuery);
        // cout.println("Unparsed qry: " + mondrian.olap.Util.unparse(query));
        Result result = connection.execute(query);

        result.print(new PrintWriter(cout, true));
        int axiscount = 1;
        if(true) {
            for(mondrian.olap.Axis a : result.getAxes()) {
                cout.println("Axis # " + axiscount++);
                int poscount = 0;
                for(mondrian.olap.Position p : a.getPositions()) {
                    cout.println(" Position # " + poscount++);
                    for(mondrian.olap.Member m : p) {
                        cout.println("  Member: " + m.getName());
                        for(mondrian.olap.Property prop : m.getProperties()) {
                            cout.println("   property: " + prop.getName());
                            Object pval = m.getPropertyValue(prop.getName());
                            String pvalstr;

                            if(pval instanceof org.postgis.PGgeometry) {
                                // property is a postgis instance
                                org.postgis.PGgeometry pggeom = (org.postgis.PGgeometry) pval;
                                // WKT geometry
                                pvalstr = pggeom.toString();
                                // pvalstr = "(geom object)";
                            }
                            else {
                                // other property type
                                pvalstr = ( pval != null ? pval.toString() : "(null)" );
                                // pvalstr = ( pval.toString() != null ? pval.toString() : "(null)" );
                            }

                            String clsName = "(null)";
                            if(pval != null) {
                                Class cls = pval.getClass();
                                if(cls != null) {
                                    clsName = cls.getName();
                                }
                            }

                            cout.println("    object type " + clsName + " ; value: " + pvalstr);
                        }
                    }
                }
            }
        }
    }

}
