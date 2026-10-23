from __future__ import annotations
from pathlib import Path
from datetime import datetime
import json,os,secrets
from flask import Flask,abort,flash,redirect,render_template,request,send_from_directory,session,url_for
from werkzeug.utils import secure_filename
from security import verify_password
from storage import ALLOWED_ASSET_EXT,MAX_ASSET,append_history,build_manifest,create_backup,history,read_json,restore_backup,validate_ui,write_json
PREFIX="/aujunpeak-admin"
DATA_DIR=Path(os.environ.get("AUJUNPEAK_DATA_DIR","/var/www/html/aujunpeak-data")).resolve()
STATE_DIR=Path(os.environ.get("AUJUNPEAK_STATE_DIR","/var/lib/aujunpeak-admin")).resolve()
BACKUP_DIR=STATE_DIR/"backups"
ADMIN_USER=os.environ.get("AUJUNPEAK_ADMIN_USER","admin")
PASSWORD_HASH=os.environ.get("AUJUNPEAK_ADMIN_PASSWORD_HASH","")
app=Flask(__name__,static_folder="static",static_url_path=f"{PREFIX}/static")
app.secret_key=os.environ.get("AUJUNPEAK_SECRET_KEY") or secrets.token_hex(32)
app.config.update(MAX_CONTENT_LENGTH=MAX_ASSET+1024*1024,SESSION_COOKIE_HTTPONLY=True,SESSION_COOKIE_SAMESITE="Lax")
SCREEN_LABELS={"main":"Main","home":"Home","game":"Game","function":"Function","settings":"Settings","profile":"Profile","login":"Login","splash":"Splash","gameBoost":"Game Boost","crosshair":"Crosshair","optimizer":"Optimizer","update":"Update","forceUpdate":"Force Update","social":"Social","about":"About","feedback":"Feedback","policy":"Policy"}
ACTION_LIST=["NONE","TOAST","DIALOG","VFX_MODAL","OPEN_URL","OPEN_APP","INTERNAL_ACTIVITY","START_FLOATING_BOOST","STOP_FLOATING_BOOST","START_CROSSHAIR","STOP_CROSSHAIR","REFRESH_DATA","RELOAD_UI","APP_SETTINGS","OVERLAY_SETTINGS","COPY_TEXT","SHARE_TEXT","CLOSE_ACTIVITY"]

def csrf_token():
    if "_csrf" not in session:session["_csrf"]=secrets.token_urlsafe(24)
    return session["_csrf"]
app.jinja_env.globals.update(csrf_token=csrf_token,prefix=PREFIX,screen_labels=SCREEN_LABELS)

@app.before_request
def guard():
    if request.path.startswith(f"{PREFIX}/static") or request.endpoint=="login":return
    if not session.get("auth"):return redirect(url_for("login",next=request.path))
    if request.method=="POST":
        token=request.form.get("_csrf") or request.headers.get("X-CSRF-Token")
        if not token or not secrets.compare_digest(token,session.get("_csrf","")):abort(400,"CSRF token không hợp lệ")

@app.route(f"{PREFIX}/login",methods=["GET","POST"])
def login():
    if request.method=="POST":
        if request.form.get("username","")==ADMIN_USER and PASSWORD_HASH and verify_password(request.form.get("password",""),PASSWORD_HASH):
            session.clear();session["auth"]=True;session["_csrf"]=secrets.token_urlsafe(24);return redirect(request.args.get("next") or url_for("dashboard"))
        flash("Sai tài khoản hoặc mật khẩu","error")
    return render_template("login.html")

@app.post(f"{PREFIX}/logout")
def logout():session.clear();return redirect(url_for("login"))

def load_all():return read_json(DATA_DIR/"ui.json",{}),read_json(DATA_DIR/"manifest.json",{})

@app.route(f"{PREFIX}/")
def dashboard():
    ui,mf=load_all();assets=mf.get("assets",[]) if isinstance(mf.get("assets"),list) else []
    st=(DATA_DIR/"ui.json").stat() if (DATA_DIR/"ui.json").exists() else None
    return render_template("dashboard.html",ui=ui,mf=mf,assets=assets,screen_count=len(ui.get("screens",{})),modified=datetime.fromtimestamp(st.st_mtime) if st else None,history=history(STATE_DIR,8))

@app.route(f"{PREFIX}/editor",methods=["GET","POST"])
def editor():
    if request.method=="POST":
        raw=request.form.get("raw","")
        try:data=json.loads(raw)
        except Exception as e:flash(f"JSON lỗi: {e}","error");return render_template("editor.html",raw=raw)
        errs=validate_ui(data)
        if errs:
            for x in errs[:12]:flash(x,"error")
            return render_template("editor.html",raw=raw)
        write_json(DATA_DIR/"ui.json",data);append_history(STATE_DIR,{"event":"save_draft","note":"Raw JSON editor"});flash("Đã lưu bản nháp. Nhấn PUBLISH để phát hành cho app.","ok");return redirect(url_for("editor"))
    raw=(DATA_DIR/"ui.json").read_text(encoding="utf-8") if (DATA_DIR/"ui.json").exists() else "{}"
    return render_template("editor.html",raw=raw)

def num(v,d):
    try:return float(v)
    except:return d

def bg_style(form):
    colors=[x.strip() for x in form.get("colors","#10182E,#0C1426").split(",") if x.strip()]
    return {"marginTopDp":num(form.get("margin_top"),10),"paddingDp":num(form.get("padding"),12),"background":{"colors":colors[:4] or ["#10182E","#0C1426"],"radiusDp":num(form.get("radius"),18),"strokeColor":form.get("stroke","#5561E1FF") or "#5561E1FF","strokeWidthDp":1}}

def make_action(form,prefix="click"):
    typ=form.get(prefix+"_action","NONE").upper()
    if typ in ("","NONE"):return None
    a={"type":typ};value=form.get(prefix+"_value","").strip()
    if typ=="TOAST":a["message"]=value or "Aujunpeak"
    elif typ=="DIALOG":a.update(title="Aujunpeak",message=value)
    elif typ=="VFX_MODAL":a.update(title="Aujunpeak",message=value,icon="fa-check",status="ĐÃ KÍCH HOẠT",accentColor="#61E1FF",accentColor2="#A189FF",autoDismissMs=1650)
    elif typ=="OPEN_URL":a["url"]=value
    elif typ=="OPEN_APP":a["package"]=value
    elif typ=="INTERNAL_ACTIVITY":a["screen"]=value or "update"
    elif typ in ("COPY_TEXT","SHARE_TEXT"):a["text"]=value
    return a

def make_component(form):
    template=form.get("template","card_switch")
    if template=="custom":
        try:
            node=json.loads(form.get("custom_json","{}"))
        except Exception as e:
            raise ValueError(f"Custom component JSON lỗi: {e}")
        errs=[]
        probe={"schema":2,"screens":{"x":{"views":{},"dynamicLayouts":[{"id":"x","enabled":True,"target":"__root__","mode":"append","components":[node]}]}}}
        errs=validate_ui(probe)
        if errs:raise ValueError("; ".join(errs[:8]))
        return node
    cid=form.get("component_id","").strip() or f"component_{secrets.token_hex(3)}";title=form.get("title","TÍNH NĂNG MỚI").strip();subtitle=form.get("subtitle","").strip();style=bg_style(form)
    if template=="card_switch":
        swid=cid+"_switch";sw={"id":swid,"type":"switch","title":form.get("switch_title","BẬT CHỨC NĂNG"),"subtitle":form.get("switch_subtitle",subtitle),"prefKey":form.get("pref_key",swid),"defaultValue":False,"style":{"marginTopDp":6,"background":{"colors":["#111A30","#0B1426"],"radiusDp":15,"strokeColor":"#4557D9FF","strokeWidthDp":1}}}
        on=make_action(form,"on");off=make_action(form,"off")
        if on:sw["onAction"]=on
        if off:sw["offAction"]=off
        return {"id":cid,"type":"card","style":style,"children":[{"type":"text","text":title,"style":{"textSizeSp":16,"textColor":"#FFFFFF"}},{"type":"text","text":subtitle,"style":{"textSizeSp":11,"textColor":"#91A5C1","marginTopDp":3,"marginBottomDp":8}},sw]}
    if template=="card_button":
        btn={"id":cid+"_button","type":"button","text":form.get("button_text","MỞ NGAY"),"style":{"marginTopDp":10,"background":{"colors":["#52D8FF","#6888FF","#9B6DFF"],"radiusDp":16,"strokeColor":"#E8FFFFFF","strokeWidthDp":1}}};act=make_action(form,"click")
        if act:btn["onClick"]=act
        return {"id":cid,"type":"card","style":style,"children":[{"type":"text","text":title,"style":{"textSizeSp":16,"textColor":"#FFFFFF"}},{"type":"text","text":subtitle,"style":{"textSizeSp":11,"textColor":"#91A5C1","marginTopDp":3}},btn]}
    if template=="switch":
        node={"id":cid,"type":"switch","title":title,"subtitle":subtitle,"prefKey":form.get("pref_key",cid),"defaultValue":False,"style":style};on=make_action(form,"on");off=make_action(form,"off")
        if on:node["onAction"]=on
        if off:node["offAction"]=off
        return node
    if template=="button":
        node={"id":cid,"type":"button","text":title,"style":style};act=make_action(form,"click")
        if act:node["onClick"]=act
        return node
    if template=="image":return {"id":cid,"type":"image","asset":form.get("asset",""),"heightDp":num(form.get("height"),160),"scaleType":"centerCrop","style":style}
    if template=="icon":return {"id":cid,"type":"icon","icon":form.get("fa_icon","fa-bolt").strip() or "fa-bolt","iconColor":form.get("icon_color","#61E1FF").strip() or "#61E1FF","sizeDp":num(form.get("icon_size"),34),"style":style}
    return {"id":cid,"type":"text","text":title,"style":style}

@app.route(f"{PREFIX}/builder",methods=["GET","POST"])
def builder():
    ui,mf=load_all();screens=ui.setdefault("screens",{})
    if request.method=="POST":
        sk=request.form.get("screen","home");screen=screens.setdefault(sk,{"views":{},"dynamicLayouts":[]});layouts=screen.setdefault("dynamicLayouts",[]);lid=request.form.get("layout_id","").strip() or f"web_{sk}_extensions";layout=next((x for x in layouts if isinstance(x,dict) and x.get("id")==lid),None)
        if not layout:layout={"id":lid,"enabled":True,"target":request.form.get("target","").strip() or ("boostStartContainer" if sk=="home" else "__root__"),"mode":request.form.get("mode","append"),"components":[]};layouts.append(layout)
        layout["enabled"]=True;layout["target"]=request.form.get("target","").strip() or layout.get("target","__root__");layout["mode"]=request.form.get("mode","append")
        try:
            node=make_component(request.form)
        except Exception as e:
            flash(str(e),"error");return redirect(url_for("builder",screen=sk))
        layout.setdefault("components",[]).append(node);write_json(DATA_DIR/"ui.json",ui);append_history(STATE_DIR,{"event":"builder_add","screen":sk,"component":node.get("id"),"type":node.get("type")});flash("Đã thêm component vào bản nháp. Kiểm tra Preview rồi Publish.","ok");return redirect(url_for("builder",screen=sk))
    selected=request.args.get("screen","home");return render_template("builder.html",ui=ui,mf=mf,selected=selected,actions=ACTION_LIST)

@app.post(f"{PREFIX}/layout/toggle")
def layout_toggle():
    ui,_=load_all();sk=request.form.get("screen","home");lid=request.form.get("layout_id");screen=ui.get("screens",{}).get(sk,{})
    for x in screen.get("dynamicLayouts",[]):
        if isinstance(x,dict) and x.get("id")==lid:x["enabled"]=not bool(x.get("enabled",True));break
    write_json(DATA_DIR/"ui.json",ui);flash("Đã đổi trạng thái layout.","ok");return redirect(url_for("builder",screen=sk))

@app.post(f"{PREFIX}/component/delete")
def component_delete():
    ui,_=load_all();sk=request.form.get("screen");lid=request.form.get("layout_id");cid=request.form.get("component_id");screen=ui.get("screens",{}).get(sk,{})
    for l in screen.get("dynamicLayouts",[]):
        if l.get("id")==lid:l["components"]=[c for c in l.get("components",[]) if not(isinstance(c,dict) and c.get("id")==cid)]
    write_json(DATA_DIR/"ui.json",ui);append_history(STATE_DIR,{"event":"component_delete","screen":sk,"component":cid});flash("Đã xóa khỏi bản nháp.","ok");return redirect(url_for("builder",screen=sk))

@app.route(f"{PREFIX}/views",methods=["GET","POST"])
def view_styler():
    ui,_=load_all();screens=ui.setdefault("screens",{})
    if request.method=="POST":
        sk=request.form.get("screen","home");view_id=request.form.get("view_id","").strip()
        if not view_id:flash("Cần nhập View ID","error");return redirect(url_for("view_styler",screen=sk))
        screen=screens.setdefault(sk,{"views":{},"dynamicLayouts":[]});cfg=screen.setdefault("views",{}).setdefault(view_id,{})
        text=request.form.get("text","");visibility=request.form.get("visibility","");alpha=request.form.get("alpha","");height=request.form.get("height","");width=request.form.get("width","")
        if text:cfg["text"]=text
        elif request.form.get("clear_text")=="1":cfg.pop("text",None)
        if visibility:cfg["visibility"]=visibility
        if alpha:cfg["alpha"]=num(alpha,1)
        if height:cfg["heightDp"]=num(height,0)
        if width:cfg["widthDp"]=num(width,0)
        colors=[x.strip() for x in request.form.get("colors","").split(",") if x.strip()]
        if colors:
            cfg["background"]={"colors":colors,"radiusDp":num(request.form.get("radius"),18),"strokeColor":request.form.get("stroke") or "#5561E1FF","strokeWidthDp":num(request.form.get("stroke_width"),1)}
        write_json(DATA_DIR/"ui.json",ui);append_history(STATE_DIR,{"event":"view_style","screen":sk,"view":view_id});flash("Đã lưu style View vào bản nháp.","ok");return redirect(url_for("view_styler",screen=sk,view=view_id))
    sk=request.args.get("screen","home");vid=request.args.get("view","");screen=screens.get(sk,{})
    return render_template("views.html",ui=ui,selected=sk,view_id=vid,cfg=screen.get("views",{}).get(vid,{}))

@app.route(f"{PREFIX}/component/edit",methods=["GET","POST"])
def component_edit():
    ui,_=load_all();sk=request.values.get("screen","home");lid=request.values.get("layout_id","");cid=request.values.get("component_id","")
    screen=ui.get("screens",{}).get(sk,{})
    layout=next((x for x in screen.get("dynamicLayouts",[]) if isinstance(x,dict) and x.get("id")==lid),None)
    if not layout:abort(404)
    idx=next((i for i,c in enumerate(layout.get("components",[])) if isinstance(c,dict) and c.get("id")==cid),None)
    if idx is None:abort(404)
    if request.method=="POST":
        try:node=json.loads(request.form.get("raw","{}"))
        except Exception as e:flash(f"JSON lỗi: {e}","error");return redirect(url_for("component_edit",screen=sk,layout_id=lid,component_id=cid))
        probe={"schema":2,"screens":{"x":{"views":{},"dynamicLayouts":[{"id":"x","enabled":True,"target":"__root__","mode":"append","components":[node]}]}}};errs=validate_ui(probe)
        if errs:
            for e in errs[:8]:flash(e,"error")
            return redirect(url_for("component_edit",screen=sk,layout_id=lid,component_id=cid))
        layout["components"][idx]=node;write_json(DATA_DIR/"ui.json",ui);append_history(STATE_DIR,{"event":"component_edit","screen":sk,"component":cid});flash("Đã sửa component trong bản nháp.","ok");return redirect(url_for("builder",screen=sk))
    return render_template("component_edit.html",screen=sk,layout_id=lid,component_id=cid,raw=json.dumps(layout["components"][idx],ensure_ascii=False,indent=2))

@app.route(f"{PREFIX}/assets",methods=["GET","POST"])
def assets():
    ad=DATA_DIR/"assets";ad.mkdir(exist_ok=True)
    if request.method=="POST":
        f=request.files.get("file")
        if not f or not f.filename:flash("Chưa chọn file.","error")
        else:
            name=secure_filename(f.filename);ext=Path(name).suffix.lower()
            if ext not in ALLOWED_ASSET_EXT:flash("Chỉ nhận PNG/JPG/JPEG/WEBP/GIF.","error")
            else:
                dest=ad/name;f.save(dest)
                if dest.stat().st_size>MAX_ASSET:dest.unlink(missing_ok=True);flash("File vượt 20 MB.","error")
                else:append_history(STATE_DIR,{"event":"asset_upload","name":name});flash("Đã upload asset. Publish để app nhận hash mới.","ok")
        return redirect(url_for("assets"))
    rows=[{"name":p.name,"size":p.stat().st_size,"mtime":datetime.fromtimestamp(p.stat().st_mtime)} for p in sorted(ad.iterdir()) if p.is_file() and not p.name.lower().startswith("readme")]
    return render_template("assets.html",assets=rows)

@app.post(f"{PREFIX}/asset/delete")
def asset_delete():
    name=secure_filename(request.form.get("name",""));p=DATA_DIR/"assets"/name
    if p.is_file():p.unlink();append_history(STATE_DIR,{"event":"asset_delete","name":name});flash("Đã xóa asset.","ok")
    return redirect(url_for("assets"))

@app.route(f"{PREFIX}/asset/<path:name>")
def asset_file(name):
    if Path(name).name!=name:abort(404)
    return send_from_directory(DATA_DIR/"assets",name)

@app.route(f"{PREFIX}/preview")
def preview():
    ui,_=load_all();sk=request.args.get("screen","home");return render_template("preview.html",ui=ui,screen=ui.get("screens",{}).get(sk,{}),selected=sk)

@app.post(f"{PREFIX}/publish")
def publish():
    try:
        backup=create_backup(DATA_DIR,BACKUP_DIR,"before_publish");mf=build_manifest(DATA_DIR,bump=True);append_history(STATE_DIR,{"event":"publish","version":mf["version"],"backup":backup.name});flash(f"PUBLISH thành công: data v{mf['version']}. App sẽ tự nhận bản mới.","ok")
    except Exception as e:flash(f"Publish thất bại: {e}","error")
    return redirect(request.form.get("next") or url_for("dashboard"))

@app.route(f"{PREFIX}/backups")
def backups():
    BACKUP_DIR.mkdir(parents=True,exist_ok=True);rows=[{"name":p.name,"size":p.stat().st_size,"mtime":datetime.fromtimestamp(p.stat().st_mtime)} for p in sorted(BACKUP_DIR.glob("*.zip"),reverse=True)];return render_template("backups.html",backups=rows,history=history(STATE_DIR,100))

@app.post(f"{PREFIX}/backup/create")
def backup_create():
    p=create_backup(DATA_DIR,BACKUP_DIR,"manual");append_history(STATE_DIR,{"event":"backup","name":p.name});flash(f"Đã tạo {p.name}","ok");return redirect(url_for("backups"))

@app.post(f"{PREFIX}/backup/restore")
def backup_restore_route():
    name=Path(request.form.get("name","")).name;p=BACKUP_DIR/name
    if not p.is_file():abort(404)
    try:
        current=create_backup(DATA_DIR,BACKUP_DIR,"before_restore");restore_backup(DATA_DIR,p);append_history(STATE_DIR,{"event":"restore","name":name,"safety":current.name});flash("Đã khôi phục backup. Publish để phát hành như version mới.","ok")
    except Exception as e:flash(f"Restore lỗi: {e}","error")
    return redirect(url_for("backups"))

@app.route(f"{PREFIX}/settings",methods=["GET","POST"])
def settings():
    ui,mf=load_all();global_cfg=ui.setdefault("global",{})
    if request.method=="POST":
        try:
            mf["minNativeVersionCode"]=max(1,int(request.form.get("min_native","13")))
            global_cfg["statusBarColor"]=request.form.get("status_bar") or global_cfg.get("statusBarColor","#050913")
            global_cfg["navigationBarColor"]=request.form.get("nav_bar") or global_cfg.get("navigationBarColor","#050913")
            pcs=[x.strip() for x in request.form.get("particle_colors","").split(",") if x.strip()]
            if pcs:global_cfg["particleColors"]=pcs
            global_cfg["particleDensity"]=max(1,min(40,int(request.form.get("particle_density",global_cfg.get("particleDensity",8)))))
            write_json(DATA_DIR/"manifest.json",mf);write_json(DATA_DIR/"ui.json",ui);append_history(STATE_DIR,{"event":"settings","minNativeVersionCode":mf["minNativeVersionCode"]});flash("Đã lưu cài đặt vào bản nháp.","ok")
        except Exception as e:flash(str(e),"error")
        return redirect(url_for("settings"))
    return render_template("settings.html",mf=mf,global_cfg=global_cfg,data_dir=str(DATA_DIR))

if __name__=="__main__":app.run(host="127.0.0.1",port=8765,debug=False)
