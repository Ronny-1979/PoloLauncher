import sys
from playwright.sync_api import sync_playwright
jobs=[("flat","style_full.json",16.2,0,0),("flat_far","style_full.json",14.6,0,0),("3d","style_full_3d.json",16.6,45,20),("3d_far","style_full_3d.json",15.4,45,340)]
with sync_playwright() as p:
    b=p.chromium.launch(args=["--use-angle=swiftshader","--enable-unsafe-swiftshader","--ignore-gpu-blocklist"])
    for name,style,z,pitch,bear in jobs:
        pg=b.new_page(viewport={"width":1280,"height":720})
        pg.goto(f"http://localhost:8123/index.html?style=http://localhost:8123/{style}&z={z}&pitch={pitch}&bearing={bear}")
        try: pg.wait_for_function("window.done===true",timeout=60000)
        except Exception as e: print("timeout",name)
        pg.wait_for_timeout(1500)
        print(name, pg.evaluate("window.errs.slice(0,5)"))
        pg.screenshot(path=f"shot_{name}.png"); pg.close()
    b.close()
