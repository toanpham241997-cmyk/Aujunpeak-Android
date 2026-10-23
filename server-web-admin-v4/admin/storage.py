from __future__ import annotations
from pathlib import Path
from datetime import datetime, timezone
import hashlib, json, os, tempfile, zipfile
MAX_UI=2*1024*1024
MAX_ASSET=20*1024*1024
ALLOWED_ASSET_EXT={".png",".jpg",".jpeg",".webp",".gif"}
COMPONENT_TYPES={"column","card","row","frame","container","scroll","text","button","switch","image","icon","input","progress","particle","divider","spacer"}
ACTION_TYPES={"","NONE","SEQUENCE","DELAY","TOAST","DIALOG","VFX_MODAL","MODAL","OPEN_URL","OPEN_APP","SHARE_TEXT","COPY_TEXT","INTERNAL_ACTIVITY","CLICK_VIEW","SET_TEXT","SET_VISIBILITY","SET_ENABLED","SET_CHECKED","SET_PREF","REMOVE_PREF","TOGGLE_PREF","REFRESH_DATA","RELOAD_UI","APP_SETTINGS","OVERLAY_SETTINGS","HTTP_REQUEST","START_FLOATING_BOOST","STOP_FLOATING_BOOST","START_CROSSHAIR","STOP_CROSSHAIR","CLOSE_ACTIVITY"}

def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()

def read_json(path: Path, default=None):
    try:return json.loads(path.read_text(encoding="utf-8"))
    except Exception:return {} if default is None else default

def atomic_text(path: Path,text: str):
    path.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix=path.name+".",dir=str(path.parent))
    try:
        with os.fdopen(fd,"w",encoding="utf-8") as f:
            f.write(text);f.flush();os.fsync(f.fileno())
        os.replace(tmp,path)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)

def write_json(path: Path,data):
    text=json.dumps(data,ensure_ascii=False,indent=2)+"\n"
    if path.name=="ui.json" and len(text.encode())>MAX_UI:raise ValueError("ui.json vượt giới hạn 2 MB")
    atomic_text(path,text)

def _walk_action(action,errors,where):
    if action is None:return
    if isinstance(action,list):
        for i,x in enumerate(action):_walk_action(x,errors,f"{where}[{i}]")
        return
    if not isinstance(action,dict):errors.append(f"{where}: action phải là object/array");return
    typ=str(action.get("type","")).upper()
    if typ not in ACTION_TYPES:errors.append(f"{where}: action không hỗ trợ: {typ}")
    if typ=="SEQUENCE":_walk_action(action.get("actions",[]),errors,where+".actions")
    if typ=="DELAY":_walk_action(action.get("action"),errors,where+".action")
    if typ=="HTTP_REQUEST":
        _walk_action(action.get("onSuccess"),errors,where+".onSuccess")
        _walk_action(action.get("onError"),errors,where+".onError")

def _walk_component(node,errors,where):
    if not isinstance(node,dict):errors.append(f"{where}: component phải là object");return
    typ=str(node.get("type","text")).lower()
    if typ not in COMPONENT_TYPES:errors.append(f"{where}: type không hỗ trợ: {typ}")
    for k in ("action","onClick","onLongClick","onAction","offAction","onChange"):
        if k in node:_walk_action(node[k],errors,f"{where}.{k}")
    children=node.get("children",[])
    if children is not None and not isinstance(children,list):errors.append(f"{where}.children phải là array")
    if isinstance(children,list):
        for i,c in enumerate(children):_walk_component(c,errors,f"{where}.children[{i}]")

def validate_ui(ui):
    errors=[]
    if not isinstance(ui,dict):return ["Root ui.json phải là object"]
    if int(ui.get("schema",2))!=2:errors.append("schema phải là 2 cho Server-Driven V2")
    screens=ui.get("screens")
    if not isinstance(screens,dict):return errors+["screens phải là object"]
    for sk,screen in screens.items():
        if not isinstance(screen,dict):errors.append(f"screens.{sk} phải là object");continue
        if not isinstance(screen.get("views",{}),dict):errors.append(f"screens.{sk}.views phải là object")
        layouts=screen.get("dynamicLayouts",[])
        if not isinstance(layouts,list):errors.append(f"screens.{sk}.dynamicLayouts phải là array");continue
        for li,layout in enumerate(layouts):
            if not isinstance(layout,dict):errors.append(f"{sk}.dynamicLayouts[{li}] phải là object");continue
            if str(layout.get("mode","append")).lower() not in {"append","prepend","overlay","replace"}:errors.append(f"{sk}.dynamicLayouts[{li}].mode không hợp lệ")
            comps=layout.get("components",[])
            if not isinstance(comps,list):errors.append(f"{sk}.dynamicLayouts[{li}].components phải là array");continue
            for ci,c in enumerate(comps):_walk_component(c,errors,f"{sk}.dynamicLayouts[{li}].components[{ci}]")
    return errors

def create_backup(data_dir:Path,backup_dir:Path,label="backup"):
    backup_dir.mkdir(parents=True,exist_ok=True)
    mf=read_json(data_dir/"manifest.json",{})
    ver=mf.get("version","x");stamp=datetime.now().strftime("%Y%m%d-%H%M%S")
    safe="".join(c if c.isalnum() or c in "-_" else "_" for c in label)[:32]
    dest=backup_dir/f"{stamp}_v{ver}_{safe}.zip"
    with zipfile.ZipFile(dest,"w",zipfile.ZIP_DEFLATED) as z:
        for name in ("ui.json","manifest.json","VIEW_ID_CATALOG.txt","README_DEPLOY.txt"):
            p=data_dir/name
            if p.is_file():z.write(p,name)
        ad=data_dir/"assets"
        if ad.exists():
            for p in ad.iterdir():
                if p.is_file():z.write(p,f"assets/{p.name}")
    return dest

def restore_backup(data_dir:Path,backup:Path):
    with zipfile.ZipFile(backup,"r") as z:
        for info in z.infolist():
            target=(data_dir/info.filename).resolve()
            if data_dir.resolve() not in target.parents and target!=data_dir.resolve():raise ValueError("ZIP không an toàn")
        z.extractall(data_dir)

def build_manifest(data_dir:Path,bump=True,exact_version=None):
    ui_path=data_dir/"ui.json";mf_path=data_dir/"manifest.json";ad=data_dir/"assets"
    if not ui_path.is_file():raise ValueError("Không tìm thấy ui.json")
    ui=read_json(ui_path,{})
    errors=validate_ui(ui)
    if errors:raise ValueError("\n".join(errors[:15]))
    old=read_json(mf_path,{})
    current=int(old.get("version",ui.get("version",1)) or 1)
    version=int(exact_version) if exact_version is not None else current+(1 if bump else 0)
    ui["schema"]=2;ui["version"]=version;write_json(ui_path,ui)
    assets=[];ad.mkdir(exist_ok=True)
    for p in sorted(ad.iterdir()):
        if not p.is_file() or p.name.startswith(".") or p.name.lower().startswith("readme"):continue
        if p.stat().st_size>MAX_ASSET:raise ValueError(f"Asset quá 20 MB: {p.name}")
        assets.append({"name":p.name,"url":f"assets/{p.name}","sha256":sha256(p)})
    mf={"schema":2,"version":version,"minNativeVersionCode":int(old.get("minNativeVersionCode",13) or 13),"config":"ui.json","configSha256":sha256(ui_path),"assets":assets}
    write_json(mf_path,mf);return mf

def append_history(state_dir:Path,event:dict):
    state_dir.mkdir(parents=True,exist_ok=True);event=dict(event);event["time"]=datetime.now(timezone.utc).isoformat()
    with (state_dir/"history.jsonl").open("a",encoding="utf-8") as f:f.write(json.dumps(event,ensure_ascii=False)+"\n")

def history(state_dir:Path,limit=100):
    p=state_dir/"history.jsonl"
    if not p.exists():return []
    out=[]
    for line in p.read_text(encoding="utf-8",errors="ignore").splitlines()[-limit:]:
        try:out.append(json.loads(line))
        except:pass
    return list(reversed(out))
