import math, os, random
from shapely.geometry import LineString, Polygon, Point, box
from shapely import affinity
import mapbox_vector_tile as mvt

random.seed(7)
LAT0, LON0 = 53.5511, 9.9937
Z = 14
def m2ll(x, y):
    return (LON0 + x / (111320 * math.cos(math.radians(LAT0))), LAT0 + y / 110574)
def ll2tile(lon, lat, z=Z):
    n = 2 ** z
    xt = (lon + 180) / 360 * n
    yt = (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n
    return xt, yt

feats = {k: [] for k in ["ocean","water_polygons","land","sites","buildings","streets","street_labels","pois",
                         "place_labels","water_lines","water_polygons_labels","water_lines_labels","public_transport",
                         "street_polygons","bridges","streets_polygons_labels","boundaries"]}
def L(pts): return LineString([m2ll(*p) for p in pts])
def P(pts): return Polygon([m2ll(*p) for p in pts])
def rect(x0, y0, x1, y1): return P([(x0,y0),(x1,y0),(x1,y1),(x0,y1)])
def add(layer, geom, **props): feats[layer].append((geom, props))

# --- land use
add("land", rect(-1700,-1700,1700,1700), kind="residential")
add("land", rect(-760,-100,-300,260), kind="commercial")
add("land", rect(300,-640,760,-330), kind="park")
add("land", rect(-1700,1200,1700,1700), kind="forest")
add("land", rect(-1700,-1700,-1000,-1000), kind="farmland")
add("land", rect(900,-300,1500,300), kind="grass")
add("land", rect(-200,-1000,300,-800), kind="industrial")
add("sites", rect(-640,-680,-440,-520), kind="hospital")
add("sites", rect(520,330,700,430), kind="parking")
# --- water: river band y=-250 plus a lake
river = [(-1700,-250),(-900,-230),(0,-270),(900,-240),(1700,-260)]
add("water_lines", L(river), kind="river", name="Elbarm", name_de="Elbarm")
add("water_polygons", LineString([m2ll(*p) for p in river]).buffer(0.0006), kind="river")
add("water_polygons", Point(*m2ll(1050, 700)).buffer(0.0035), kind="water")
add("water_polygons_labels", Point(*m2ll(1050,700)), kind="water", name="Mühlteich", name_de="Mühlteich", way_area=60000000)
add("water_lines_labels", L(river), kind="river", name="Elbarm", name_de="Elbarm")
# --- streets
NAMES = ["Rosenweg","Lindenallee","Birkenstraße","Schulstraße","Gartenweg","Bahnhofstraße","Kirchplatz","Eichenring","Am Markt","Poststraße","Mozartstraße","Goethestraße"]
def street(pts, kind, name=None, ref=None, **kw):
    g = L(pts)
    props = dict(kind=kind, **kw)
    add("streets", g, **props)
    lab = dict(kind=kind)
    if name: lab.update(name=name, name_de=name)
    if ref: lab["ref"] = ref
    if name or ref: add("street_labels", g, **lab)
street([(-1700,1100),(1700,1100)], "motorway", ref="A7")
street([(-1700,1085),(1700,1085)], "motorway", ref="A7", oneway_reverse=True)
street([(900,1100),(1000,900),(1250,600),(1300,300)], "motorway", link=True)
street([(0,-1700),(0,-330)], "primary", "Hauptstraße", ref="B5")
street([(0,-330),(0,-190)], "primary", "Hauptstraße", ref="B5", bridge=True)
street([(0,-190),(0,1100)], "primary", "Hauptstraße", ref="B5")
street([(-1700,300),(1700,300)], "secondary", "Marktallee", ref="L12")
street([(-300,-700),(300,-700)], "secondary", "Elbtunnel", tunnel=True)
street([(-1700,-700),(-300,-700)], "secondary", "Elbtunnel")
street([(300,-700),(1700,-700)], "secondary", "Elbtunnel")
for x in (-420, 420):
    street([(x,-1700),(x,-330)], "tertiary", "Ringstraße")
    street([(x,-330),(x,-190)], "tertiary", "Ringstraße", bridge=True)
    street([(x,-190),(x,1100)], "tertiary", "Ringstraße")
n = 0
for y in range(-600, 701, 100):
    if abs(y-300) < 5 or abs(y+250) < 120: continue
    street([(-800,y),(800,y)], "residential", NAMES[n % len(NAMES)], oneway=(n % 3 == 0)); n += 1
for x in range(-800, 801, 100):
    if abs(x) < 5 or abs(abs(x)-420) < 5: continue
    street([(x,-120),(x,700)], "residential", NAMES[n % len(NAMES)]); n += 1
street([(150,150),(300,150),(300,260)], "pedestrian", "Fußgängerzone")
street([(120,180),(120,60),(260,60)], "living_street", "Spielstraße")
street([(300,-640),(760,-330)], "footway")
street([(330,-400),(700,-600)], "path")
street([(-760,-40),(-300,-40)], "service")
street([(-250,500),(-250,1000)], "track")
add("street_polygons", rect(110,150,310,270), kind="pedestrian")
add("streets_polygons_labels", Point(*m2ll(210,210)), kind="pedestrian", name="Altstadtmarkt", name_de="Altstadtmarkt")
# rail
add("streets", L([(-1700,-950),(1700,-950)]), kind="rail")
# --- buildings (blocks between residential lines)
for bx in range(-780, 780, 100):
    for by in range(-580, 680, 100):
        if abs(bx) < 20 or abs(abs(bx+50)-420) < 55 and False: continue
        if by+50 > 250 and by < 350: continue
        if -330 < by < -150: continue
        if abs(bx+50) < 25 or abs(bx+50-420) < 25 or abs(bx+50+420) < 25: continue
        w, h = random.choice([(70,40),(60,60),(80,35),(45,70)])
        x0, y0 = bx+15, by+15
        add("buildings", rect(x0, y0, x0+w, y0+h), dummy=0)
add("buildings", rect(-760,-100,-560,40), dummy=0)
add("buildings", rect(-540,60,-330,220), dummy=0)
# --- pois (z14)
POIS = [("amenity","restaurant","Zum Anker",(60,180)),("amenity","cafe","Café Elbblick",(-60,170)),
        ("amenity","pharmacy","Apotheke am Markt",(180,330)),("shop","bakery","Bäckerei Schmidt",(-180,330)),
        ("tourism","hotel","Hotel Hanseatic",(250,110)),("amenity","bank","Sparkasse",(-250,260)),
        ("amenity","fuel","Aral",(-540,330)),("amenity","parking","P Markt",(600,380)),
        ("amenity","hospital","Klinikum Nord",(-540,-600)),("leisure","playground","Spielplatz",(500,-450)),
        ("shop","supermarket","Edeka",(-100,-60)),("amenity","school","Grundschule",(-300,-300)),
        ("tourism","museum","Stadtmuseum",(20,230)),("amenity","post_office","Post",(-20,340)),
        ("historic","castle","Burg Rotenfels",(1000,-500)),("amenity","fast_food","Imbiss",(140,-80)),
        ("amenity","pub","Alte Brauerei",(-340,160)),("shop","clothes","Mode Hansen",(90,320)),
        ("amenity","charging_station","Ladepunkt",(-480,-60)),("amenity","library","Bibliothek",(330,170))]
for key, val, name, (x, y) in POIS:
    add("pois", Point(*m2ll(x, y)), **{key: val, "name": name, "name_de": name})
add("public_transport", Point(*m2ll(-120, 420)), kind="station", name="Hauptbahnhof", name_de="Hauptbahnhof")
add("public_transport", Point(*m2ll(1400, 900)), kind="airfield", name="Flugplatz", name_de="Flugplatz")
add("place_labels", Point(*m2ll(0, -120)), kind="suburb", name="Altstadt", name_de="Altstadt", population=0)
add("place_labels", Point(*m2ll(-1100, 700)), kind="village", name="Neuenfelde", name_de="Neuenfelde", population=900)
add("place_labels", Point(*m2ll(1250, -800)), kind="hamlet", name="Hinterdorf", name_de="Hinterdorf", population=80)

# --- tiles
def tile_coords(geom, tx, ty):
    def f(x, y):
        xt, yt = ll2tile(x, y)
        return ((xt - tx) * 4096, (yt - ty) * 4096)
    g = geom
    from shapely.ops import transform
    return transform(lambda x, y, z=None: f(x, y), g)

xs, ys = [], []
for c in [(-1700,-1700),(1700,1700)]:
    xt, yt = ll2tile(*m2ll(*c)); xs.append(xt); ys.append(yt)
x_min, x_max = int(min(xs)), int(max(xs)); y_min, y_max = int(min(ys)), int(max(ys))
os.makedirs("tiles", exist_ok=True)
count = 0
for tx in range(x_min, x_max + 1):
    for ty in range(y_min, y_max + 1):
        layers = []
        for name, items in feats.items():
            out = []
            for geom, props in items:
                tg = tile_coords(geom, tx, ty)
                clip = tg.intersection(box(-64, -64, 4096+64, 4096+64))
                if clip.is_empty: continue
                out.append({"geometry": clip, "properties": props})
            if out: layers.append({"name": name, "features": out})
        data = mvt.encode(layers, default_options={"y_coord_down": True, "extents": 4096})
        d = f"tiles/{Z}/{tx}"; os.makedirs(d, exist_ok=True)
        open(f"{d}/{ty}.pbf", "wb").write(data); count += 1
print("tiles", count, "x", x_min, x_max, "y", y_min, y_max)
