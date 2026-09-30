import com.dashboard_test.DashboardBuilder;
import java.io.File;
public class Probe {
    public static void main(String[] a) throws Exception {
        String out="harness_probe_out"; new File(out).mkdirs();
        String[] base = new String[207]; java.util.Arrays.fill(base, "");
        base[1]="coefplot"; base[2]="probe"; base[3]="default"; base[189]=out;
        base[161]="mpg~mpg|weight~mpg|weight|foreign"; base[162]="-238.9~-49.5|1.75~-49.5|1.75|3673";
        base[163]="-347~-171|1.05~-171|1.05|2308"; base[164]="-130~72|2.45~72|2.45|5037";
        base[165]="53.1~61|0.35~61|0.35|684"; base[166]="0.000~0.42|0.000~0.42|0.000|0.000";
        base[167]="m1~m2~m3"; base[169]="74~74~74"; base[170]="price"; base[171]="h"; base[173]="whisker";
        base[181]="1"; base[182]="regress"; base[184]="-4.5~-0.81|5.0~-0.81|5.0|5.37"; base[185]="0";
        // candidate single-group shapes the ado might really send
        String[][] muts = {
            {"169","74"}, {"184","-4.5|5.0|5.37"}, {"184",""}, {"170","price~price~price"}, {"182","regress~regress~regress"},
            {"194","Mileage (mpg)|Weight (lbs.)|Car origin"}, {"194","Mileage (mpg)~Mileage (mpg)|Weight (lbs.)~Mileage (mpg)|Weight (lbs.)|Car origin"},
            {"191","r2=0.22|N=74"}, {"191","r2=0.22~r2=0.29~r2=0.35"}, {"180","1b.rep78"}, {"180","~~"}, {"195","mpg|||MPG"},
            {"168","0|Controls"}, {"186","Simple"}, {"186","Simple~Controls~Full"}, {"172","bar"}, {"183","1"}, {"174","circle rect triangle"},
            {"197","~~"}, {"198","~~"}, {"199",""}, {"190","1"},
        };
        int n=0;
        for (String[] m : muts) {
            String[] x = base.clone(); x[Integer.parseInt(m[0])] = m[1]; if(m[0].equals("197")||m[0].equals("198")){x[197]="~~";x[198]="~~";} x[4] = out + "/p" + (n++) + ".html";
            int rc = DashboardBuilder.execute(x);
            System.out.println((rc==0?"ok   ":"FAIL ") + "arg" + m[0] + " = \"" + m[1] + "\"");
        }
    }
}
