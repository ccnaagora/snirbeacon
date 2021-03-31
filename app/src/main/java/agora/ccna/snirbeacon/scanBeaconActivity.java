package agora.ccna.snirbeacon;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/*
PENSER A VALIDER LES AUTORISATIONS DANS LES PARAMETRES DE L'APPLICATION

Les beacon envoient entre autre à intervalles réguliers 3 data:
UUID
MAJOR
MINOR
Ils rtransmettent aussi le puissance étalonnée à 1m en dbm

Il existe cependant des différences selon les fabricants de beacon
format de la trame à checker: dépend des fabricants
m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25

Signification
m - matching byte sequence for this beacon type to parse (exactly one required)
s - UUID du type de beacon à capturer (optional, only for Gatt-based beacons)
i - identifiant : de 1 à 3
p - champ de calibration de puissance (nécessaire)
d - champ de données (optional, multiple allowed)
x - extra layout. Signifies that the layout is secondary to a primary layout with the same matching byte sequence (or ServiceUuid). Extra layouts do not require power or identifier fields and create Beacon objects without identifiers.

Example of a parser string for AltBeacon:
"m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"
=> Décodé si :
    data[2 3] = 0xbeac
    id1 = data[4  19]   //16 octets
    id2 = data[20-21]
    id3 = data[22-23]
    p = data[24]
    données = data[25]

Exemple de fabricants
IBEACON        m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24        //pas de champs data

ALTBEACON      m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25
EDDYSTONE TLM  x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15
EDDYSTONE UID  s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19
EDDYSTONE URL  s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v

Les regions permettent de définir un ensemble de beacon via leur UUID
Plusieurs beacon pauvent avoir le meme UUID, ils appartiennent alors à la même région.
Dans ce cas, on différencie les beacon via les MAJOR ou MINOR

L'API android permet de scanner ou monitorer les beacon en fonction de leur region ou
si celles-ci ne sont pas définie, en fonction des autres paramètres
 */
public class scanBeaconActivity extends AppCompatActivity implements BeaconConsumer{

    //gestionnaire du beacon
    private BeaconManager beam;
    //region afin de réunir NOS beacon sous la même appellation
    private Region region ;
    Button bstop;
    Button bstart;
    TextView ed;
    String ars ;
    int nbscan;
    //tache asynchron interne (plus facile) qui gère le scan à intervalle régulier
    scanAsynch as;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        Log.i("SNIR" , "oncreate Activity");
        //region labélée MUSEE sans idn particulier (attention, scan de tous les beacons
        region = new Region("MUSEE" , null ,null, null);
        ars = new String("");
        nbscan=0;
        //*****************init widget
        bstart = (Button)findViewById(R.id.button2);
        bstop  = (Button)findViewById(R.id.button);
        ed     = (TextView) findViewById(R.id.ed);
        bstart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //démarrage de la tache asynchrone (thread) de scan des beacon
                //cette tache affichera ses résultats directement dans le textView
                as = new scanAsynch();
                as.execute();
            }});
        bstop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    //mettre un terme au scan
                    as.a=false;
                    beam.stopRangingBeaconsInRegion(region);
                    ed.setText("Scan inactif.");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        //****fin de init widget
        verifyBluetooth();
        initBeaconManager();
    }
    //**********************init beaconManager
    public void initBeaconManager(){
        BeaconManager.setManifestCheckingDisabled(true);
        beam = BeaconManager.getInstanceForApplication(this);
        //attention si android > 6 => time > 6ms
        beam.setForegroundScanPeriod(5001);
        beam.setForegroundBetweenScanPeriod(101);
        //beam.setForegroundScanPeriod(1100l);
        //    beam.setBackgroundScanPeriod(10000);
        //    beam.setDebug(true);

        //entetes des trames bluetouthà scanner, dépend des fabricants
        //doit être paramétré dans le parser si nécessaire (sauf le beac qui l'est par defaut).
        //02011a1aff 1801 1234 2f234454cf6d4a0fadf2f4911ba9ffa600010002c5");
        //02011a1bff beac 2f23 4454cf6d4a0fadf2f4911ba9ffa600010002c509000000
        //beam.getBeaconParsers().add(new BeaconParser().
        //        setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        
        //necessaire pour les ibeacon du lycée
        beam.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        //beam.getBeaconParsers().add(new BeaconParser().
        //        setBeaconLayout("m:2-3=1234,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        //attachement du gestionnaire à l'application
        beam.bind(this);
    }
    @Override
    public void onBeaconServiceConnect() {
        Log.i("SNIR" , "onBeaconServiceConnect.");
    }

    //************************START SCAN BEACON
    public void startScanning() {
        //Start scanning .
        beam.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                //événement déclenché par le scan du beacon, traitement des beacon détéctés
                if (beacons.size() > 0) {
                    ars= "";
                    nbscan++;
                    Iterator<Beacon> beaconIterator = beacons.iterator();
                    while (beaconIterator.hasNext()) {
                        Beacon beacon = beaconIterator.next();
                        //fabrication du String pour chaque beacon scanné
                        String tmp = "adr=";
                        tmp += beacon.getBluetoothAddress();
                        tmp += ("\ndist=" + beacon.getDistance());
                        tmp += ("\nid1=" +       beacon.getId1());
                        tmp += ("\nid2=" +       beacon.getId2());
                        tmp += ("\nid3=" +       beacon.getId3());
                        tmp += ("\nrssi=" + beacon.getRssi());
                        //tmp += ("  uuid=" + beacon.getServiceUuid());
                        tmp +=  "%";
                        ars += tmp;
                        /*
                        //si débugage nécessaire
                        Log.i("SNIR" ,  tmp);
                        Log.i("SNIR" ,  "Region: "+region.getUniqueId()+"\t" + region.toString());
                        Log.i("SNIR" ,  "Blutoothname\t" + beacon.getBluetoothName());
                        Log.i("SNIR" ,  "ParserIdentifier\t" + beacon.getParserIdentifier());
                        Log.i("SNIR" ,  "uniqueID\t" + region.getUniqueId());
                                List<Long> lst = beacon.getDataFields();
                                for(int i=0;i<lst.size();i++){
                                    Log.i("SNIR" ,  "\tliste data fields: " + lst.get(i));
                                }
                        */
                    }
                }
                else
                    Log.i("SNIR" , "pas de beacon: "+ region.getUniqueId());
            }
        });

        try {
            //si region doit cibler un beacon particulier dont on connait l'adresse MAC
            //region = new Region("AAAA" , "C2:D1:12:75:C2:02");
            beam.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            // TODO - OK, what now then?
        }
    }
    //****************************Fin de scan beacon
    //*************verifier si attachement possible au service beacon
    private void verifyBluetooth() {
        try {
            if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Bluetooth non activé");
                builder.setMessage("Activer le bluetooth et redémarrer l'application.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                        System.exit(0);
                    }
                });
                builder.show();
            }
        }
        catch (RuntimeException e) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE non disponible");
            builder.setMessage("Le device ne supporte pas le bluetooth LE.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                    System.exit(0);
                }

            });
            builder.show();
        }
    }
//classe asynchtask pour gérer la recherche de beacon
/*
Les arguments du asynchtask
Le premier est le type des paramètres fournis à la tâche
Le second est le type de données transmises durant la progression du traitement : String tmp du scan
Enfin le troisième est le type du résultat de la tâche : peu importe
 */

    public class scanAsynch extends AsyncTask<Void , String , String> {

        public  boolean a;
        @Override
        protected String doInBackground(Void... params) {
            a = true;
            String[] x = {"1" , "2" , "3"};
            //paramétrage et démarrage du scan
            startScanning();
            while(  a == true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //après 1s, on affiche les nouveaux beacons détéctés.
                publishProgress(ars);//appel surveillé de onProgressUpdate
            }
            return null;
        }
        @Override
        protected void onProgressUpdate(String... x){
            super.onProgressUpdate(x);
            //mise à jour de l'affichage des nouveaux beacon
            ed.setText("");
            for(int i=0 ; i< x.length;i++) {
                String[] tab = x[i].split("%");
                if(tab.length >0) {
                    for(int j=0;j<tab.length;j++){
                        ed.append(tab[j]);
                        ed.append("\n########################\n");
                    }
                }
                else ed.setText("Erreur:" + tab.length);

            }
        }
    }
    //******fin de la asynchtask
}
