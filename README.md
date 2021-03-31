# snirbeacon
scan de beacon : 

PENSER A VALIDER LES AUTORISATIONS DANS LES PARAMETRES DE L'APPLICATION


Les beacon envoient entre autre à intervalles réguliers 3 data:
UUID
MAJOR
MINOR


Ils retransmettent aussi le puissance étalonnée à 1m en dbm
Il existe cependant des différences selon les fabricants de beacon.


format de la trame à checker: dépend des fabricants
m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25
Signification


m - matching byte sequence for this beacon type to parse (exactly one required)
s - UUID du type de beacon à capturer (optional, only for Gatt-based beacons)
i - identifiant : de 1 à 3
p - champ de calibration de puissance (nécessaire)
d - champ de données (optional, multiple allowed)
x - extra layout. Signifies that the layout is secondary to a primary layout with the same matching byte sequence (or ServiceUuid). 
<br>
Example du parser pour AltBeacon:
"m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"
=> Décodé si :
    data[2 3] = 0xbeac
    id1 = data[4  19]   //16 octets
    id2 = data[20-21]
    id3 = data[22-23]
    p = data[24]
    données = data[25]
Structure des parsers pour certains fabricants:
IBEACON        m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24        //pas de champs data
ALTBEACON      m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25
EDDYSTONE TLM  x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15
EDDYSTONE UID  s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19
EDDYSTONE URL  s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20

Les regions permettent de définir un ensemble de beacon via leur UUID
Plusieurs beacon pauvent avoir le meme UUID, ils appartiennent alors à la même région.
Dans ce cas, on différencie les beacon via les MAJOR ou MINOR
L'API android permet de scanner ou monitorer les beacon en fonction de leur region ou
si celles-ci ne sont pas définie, en fonction des autres paramètres

L'exemple ci-dessous démarre un scan et affiche les beacon trouvés.
La region n'est pas définie, tous les beacons sont scannés.
Pas de monitoring
