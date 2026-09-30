package com.dashboard_test.html;

import com.dashboard_test.data.DataSet;

/**
 * PubTable -- publication-quality post-estimation table (v3.6.0-t2h, batch 2a).
 *
 * A NEW class (deliberately NOT inside ChartRenderer or HtmlGenerator per the
 * t2g handover, section 5) that owns the estout/esttab-style table layer on top
 * of the existing post-estimation infrastructure. It renders TWO things and
 * emits the JS that drives them:
 *
 *   1. An on-page, interactive (sortable) publication grid, built client-side
 *      from the SAME window.__tblData object HtmlGenerator already emits (for
 *      coefplot/eventstudy) or from window.__tblMargins (for marginsplot, which
 *      PubTable emits itself). Because the chart and the table read one source,
 *      they can never disagree.
 *   2. Exports from that one object: Markdown (the Copy default), LaTeX
 *      (booktabs + threeparttable, \caption/\label, standalone-compilable) and
 *      CSV (tidy long: model, term, b, se, ll, ul, p).
 *
 * The existing regress-style table (HtmlGenerator.buildPostEstTable) is kept,
 * unchanged, behind a [Publication | Detailed] toggle -- the "keep both" choice.
 *
 * All DOM access in the emitted JS is defensive (guards every getElementById)
 * so the script is safe under verify/js_check.js's mock document, which returns
 * truthy stubs and fails the run on any thrown error. ASCII only throughout.
 */
public class PubTable {

    private final HtmlGenerator gen;
    private final DashboardOptions o;

    public PubTable(HtmlGenerator gen, DashboardOptions o) {
        this.gen = gen;
        this.o = o;
    }

    // ------------------------------------------------------------------ block
    /** The visible table block (toolbar + views). Empty when notable or no data. */
    public String buildBlock(DataSet data) {
        if (o.table.noTable) return "";
        if (o.type.equals("marginsplot")) {
            return o.chart.mpData.isEmpty() ? "" : buildMarginsBlock();
        }
        if (!o.chart.peNames.isEmpty()) return buildCoefBlock(data);
        return "";
    }

    /** The JS that renders and exports the block. Empty when notable or no data. */
    public String buildJs() {
        if (o.table.noTable) return "";
        if (o.type.equals("marginsplot")) {
            return o.chart.mpData.isEmpty() ? "" : commonJs() + marginsDataJs() + marginsJs();
        }
        if (!o.chart.peNames.isEmpty()) return commonJs() + coefJs();
        return "";
    }

    // =====================================================================
    // COEF / EVENTSTUDY BLOCK
    // =====================================================================
    private String buildCoefBlock(DataSet data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='coefplot-wrap spk-pub-wrap' style='margin-top:1rem;'>\n");
        sb.append(toolbar(true));
        // Publication view (default): filled by _spkRenderPub()
        sb.append("<div id='spkPubView'>\n");
        // t2j fix3(D): both tables live in ONE horizontal-scroll container so that, when
        // many models make the fixed columns exceed the page width, the coefficient grid and
        // the goodness-of-fit footer scroll together and stay column-aligned.
        sb.append("  <div id='spkPubScroll' style='overflow-x:auto;'>\n");
        sb.append("    <div id='spkPubInner'>\n");
        sb.append("      <div id='spkPubTable'></div>\n");
        if (!o.table.noFooter) sb.append("      <div id='spkPubFooter'></div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");
        // Detailed view (hidden): the existing regress-style table, untouched.
        sb.append("<div id='spkDetailView' hidden>\n");
        sb.append(gen.buildPostEstTable());
        sb.append("</div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    // =====================================================================
    // MARGINSPLOT BLOCK (no classic table exists for marginsplot -> pub only)
    // =====================================================================
    private String buildMarginsBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='coefplot-wrap spk-pub-wrap' style='margin-top:1rem;'>\n");
        sb.append(toolbar(false));
        sb.append("<div id='spkPubView'>\n");
        sb.append("  <div id='spkPubTable' style='overflow-x:auto;'></div>\n");
        if (!o.table.noFooter) sb.append("  <div id='spkPubFooter'></div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ toolbar
    /** Export dropdowns (Copy / Download), optional view toggle, sort hint. */
    private String toolbar(boolean hasDetail) {
        boolean dark = gen.isDark();
        String bg   = dark ? "#2a2a4a" : "#f4f4f4";
        String col  = dark ? "#d0d0e8" : "#444444";
        String bd   = dark ? "#44447a" : "#c8c8c8";
        String menuBg = dark ? "#1a1a2e" : "#ffffff";
        String lab  = dark ? "#888899" : "#aaaaaa";
        String btn = "background:" + bg + ";color:" + col + ";border:1px solid " + bd
            + ";border-radius:3px;padding:2px 9px;font-size:10px;font-weight:600;cursor:pointer;"
            + "font-family:inherit;letter-spacing:.03em;";
        String labS = "font-size:10px;color:" + lab + ";margin:0 2px 0 6px;font-family:inherit;";
        String menu = "position:absolute;top:100%;left:0;margin-top:2px;background:" + menuBg
            + ";border:1px solid " + bd + ";border-radius:4px;box-shadow:0 4px 14px rgba(0,0,0,.25);"
            + "z-index:50;min-width:150px;padding:3px;";
        String item = "display:block;width:100%;text-align:left;background:none;border:none;color:" + col
            + ";padding:5px 10px;font-size:11px;cursor:pointer;font-family:inherit;border-radius:3px;";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='spk-pub-toolbar' style='display:flex;flex-wrap:wrap;align-items:center;")
          .append("justify-content:flex-end;gap:5px;margin:2px 0 8px 0;'>\n");
        // -- Copy dropdown --
        sb.append("<span style='").append(labS).append("'>Export:</span>");
        sb.append("<span style='position:relative;display:inline-block;'>")
          .append("<button style='").append(btn).append("' onclick=\"_spkPubMenu(this)\">Copy &#9662;</button>")
          .append("<div style='").append(menu).append("' hidden>")
          .append("<button style='").append(item).append("' onclick=\"_spkPubExport('copy-md');_spkPubHideMenus()\">Markdown</button>")
          .append("<button style='").append(item).append("' onclick=\"_spkPubExport('copy-latex');_spkPubHideMenus()\">LaTeX (booktabs)</button>")
          .append("<button style='").append(item).append("' onclick=\"_spkPubExport('copy-tsv');_spkPubHideMenus()\">Plain text (TSV)</button>")
          .append("</div></span>");
        // -- Download dropdown --
        sb.append("<span style='position:relative;display:inline-block;'>")
          .append("<button style='").append(btn).append("' onclick=\"_spkPubMenu(this)\">Download &#9662;</button>")
          .append("<div style='").append(menu).append("' hidden>")
          .append("<button style='").append(item).append("' onclick=\"_spkPubExport('dl-csv');_spkPubHideMenus()\">CSV (tidy long)</button>")
          .append("<button style='").append(item).append("' onclick=\"_spkPubExport('dl-tex');_spkPubHideMenus()\">LaTeX .tex (booktabs)</button>")
          .append("<button style='").append(item).append("' onclick=\"_spkPrintOnly('table');_spkPubHideMenus()\">PDF (table only)</button>")
          .append("</div></span>");
        // -- view toggle (coef only) --
        if (hasDetail) {
            String tglBg = dark ? "#3a3a6a" : "#d0d8e8";
            String tgl = "cursor:pointer;font-family:inherit;padding:2px 9px;font-size:10px;font-weight:600;color:" + col + ";";
            sb.append("<span style='").append(labS).append("'>View:</span>");
            sb.append("<span style='display:inline-flex;'>")
              .append("<button id='_pubTabPub' style='").append(tgl)
              .append("background:").append(tglBg).append(";border:1px solid ").append(bd)
              .append(";border-radius:3px 0 0 3px;' onclick=\"_spkTblView('pub')\">Publication</button>")
              .append("<button id='_pubTabDet' style='").append(tgl)
              .append("background:").append(bg).append(";border:1px solid ").append(bd)
              .append(";border-left:none;border-radius:0 3px 3px 0;font-weight:400;' onclick=\"_spkTblView('detail')\">Detailed</button>")
              .append("</span>");
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    // =====================================================================
    // COMMON JS (helpers shared by coef + margins; _spkPub prefix, self-contained)
    // =====================================================================
    private String commonJs() {
        StringBuilder js = new StringBuilder();
        js.append("\n/* v3.6.0-t2h (batch 2a): PubTable -- publication table + exports */\n");
        // dropdown menu toggle + outside-click close
        js.append("function _spkPubMenu(btn){var m=btn.nextElementSibling;if(!m)return;")
          .append("var open=!m.hidden;")
          .append("var all=document.querySelectorAll?document.querySelectorAll('.spk-pub-toolbar div'):[];")
          .append("for(var i=0;i<all.length;i++){all[i].hidden=true;}")
          .append("m.hidden=open;}\n");
        // t2j fix2: close (retract) the open export menu after an item is chosen.
        // A menu item is INSIDE .spk-pub-toolbar, so the outside-click handler below
        // does not fire for it -- without this the menu stayed open after Copy/Download.
        js.append("function _spkPubHideMenus(){var all=document.querySelectorAll?document.querySelectorAll('.spk-pub-toolbar div'):[];for(var i=0;i<all.length;i++)all[i].hidden=true;}\n");
        js.append("if(document&&document.addEventListener){document.addEventListener('click',function(e){")
          .append("var t=e&&e.target;var inTb=false;while(t){if(t.className&&(''+t.className).indexOf('spk-pub-toolbar')>=0){inTb=true;break;}t=t.parentNode;}")
          .append("if(!inTb){var all=document.querySelectorAll?document.querySelectorAll('.spk-pub-toolbar div'):[];for(var i=0;i<all.length;i++)all[i].hidden=true;}});}\n");
        // view toggle
        js.append("function _spkTblView(w){var p=document.getElementById('spkPubView');var d=document.getElementById('spkDetailView');")
          .append("if(p)p.hidden=(w!=='pub');if(d)d.hidden=(w!=='detail');")
          .append("var bp=document.getElementById('_pubTabPub');var bd=document.getElementById('_pubTabDet');")
          .append("if(bp)bp.style.fontWeight=(w==='pub')?'600':'400';if(bd)bd.style.fontWeight=(w==='detail')?'600':'400';}\n");
        // tiny self-contained helpers (do not depend on buildTblExportJs)
        js.append("function _spkPubDl(text,fname,mime){try{var b=new Blob([text],{type:mime});var a=document.createElement('a');")
          .append("a.href=URL.createObjectURL(b);a.download=fname;document.body.appendChild(a);a.click();document.body.removeChild(a);")
          .append("setTimeout(function(){URL.revokeObjectURL(a.href);},60000);}catch(e){}}\n");
        js.append("function _spkPubCopy(text){try{if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text);return;}}catch(e){}")
          .append("try{var ta=document.createElement('textarea');ta.value=text;ta.style.position='fixed';ta.style.left='-9999px';")
          .append("document.body.appendChild(ta);ta.select();document.execCommand('copy');document.body.removeChild(ta);}catch(e){}}\n");
        // LaTeX escaper (mirrors buildTblExportJs._spkLatexEsc; < > last)
        js.append("function _spkPubLx(s){if(s==null)return '';s=String(s);")
          // strip C0 controls + JS line/paragraph separators: they have no LaTeX
          // meaning and U+2028/U+2029 abort pdflatex (t2i hardening).
          .append("s=s.replace(/[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u2028\\u2029]/g,' ');")
          .append("s=s.replace(/\\\\/g,'\\\\textbackslash{}');")
          .append("s=s.replace(/&/g,'\\\\&').replace(/%/g,'\\\\%').replace(/\\$/g,'\\\\$');")
          .append("s=s.replace(/#/g,'\\\\#').replace(/_/g,'\\\\_');")
          .append("s=s.replace(/\\{/g,'\\\\{').replace(/\\}/g,'\\\\}');")
          .append("s=s.replace(/~/g,'\\\\textasciitilde{}').replace(/\\^/g,'\\\\textasciicircum{}');")
          .append("s=s.replace(/</g,'$<$').replace(/>/g,'$>$');return s;}\n");
        // t2j fix4 (ported from Astra rc1, improved): spreadsheet formula-injection guard.
        // Neutralises a leading =, +, @ (after optional whitespace) by prefixing a single
        // quote (Excel/Sheets treat a leading apostrophe as a text marker). A leading '-'
        // is guarded ONLY when it does not begin a number, so genuine negative coefficients
        // like -49.5122 are preserved (Astra's guard space-prefixes every leading '-').
        js.append("function _spkSheetSafe(s){s=String(s==null?'':s);var t=s.replace(/^\\s+/,'');")
          .append("if(t.length===0)return s;var c=t.charAt(0);")
          .append("if(c==='='||c==='+'||c==='@')return \"'\"+s;")
          .append("if(c==='-'&&!/^-\\d*\\.?\\d/.test(t))return \"'\"+s;return s;}\n");
        // CSV field escaper: sheet-safe first, then RFC-4180 quoting.
        js.append("function _spkPubCsv(s){if(s==null)return '';s=_spkSheetSafe(String(s));")
          .append("if(/[,\"\\n\\r]/.test(s))return '\"'+s.replace(/\"/g,'\"\"')+'\"';return s;}\n");
        // Markdown cell escaper (pipes and newlines)
        js.append("function _spkPubMd(s){if(s==null)return '';return String(s).replace(/\\|/g,'\\\\|').replace(/\\n/g,' ');}\n");
        return js.toString();
    }

    // =====================================================================
    // COEF JS: normalise __tblData -> render sortable grid + MD/LaTeX/CSV
    // =====================================================================
    private String coefJs() {
        StringBuilder js = new StringBuilder();
        // Build a normalised model from __tblData once.
        js.append("function _spkPubModel(){var d=window.__tblData;if(!d)return null;")
          .append("var nM=d.models.length;var lbl=function(n){return (window._spkCoefLabel?_spkCoefLabel(n):n);};")
          // term union in first-appearance order
          .append("var order=[],seen={};for(var m=0;m<nM;m++){var rs=d.models[m].rows;for(var i=0;i<rs.length;i++){var nm=rs[i].name;if(!seen[nm]){seen[nm]=1;order.push(nm);}}}")
          .append("var look=[];for(var m2=0;m2<nM;m2++){var mp={};var r2=d.models[m2].rows;for(var j=0;j<r2.length;j++)mp[r2[j].name]=r2[j];look.push(mp);}")
          .append("var terms=order.map(function(nm){var cells=[];for(var m3=0;m3<nM;m3++){cells.push(look[m3][nm]||null);}return {name:nm,disp:lbl(nm),cells:cells};});")
          .append("var cols=[];for(var c=0;c<nM;c++){var L=d.models[c].label||'';cols.push({num:'('+(c+1)+')',label:L});}")
          .append("var ex=window.__tblExtras||{indicators:[],addstats:[]};")
          // headingByName: resolve each heading to the term it sits above. headings()
          // may name the coefficient (matches ChartRenderer) OR give a 1-based position
          // (the documented headings(1 \"...\"|3 \"...\") form) -> map to that term's name.
          .append("var tnames={};for(var ti=0;ti<terms.length;ti++)tnames[terms[ti].name]=1;")
          .append("var hbn={};var hh=d.headings||[];for(var h=0;h<hh.length;h++){var nm=hh[h].name;var key=nm;")
          .append("if(!tnames[nm]){var p=parseInt(nm,10);if(!isNaN(p)&&p>=1&&p<=terms.length)key=terms[p-1].name;}hbn[key]=hh[h].text;}")
          .append("return {mode:'coef',nM:nM,cols:cols,terms:terms,headings:hh,headingByName:hbn,bases:(d.bases||[]).map(lbl),")
          .append("indicators:ex.indicators||[],addstats:ex.addstats||[],models:d.models,")
          .append("showT:!!d.showT,showCI:!!d.showCI,noStars:!!d.noStars,noFooter:!!d.noFooter,")
          .append("starLegend:d.starLegend||'',ciLevel:d.ciLevel||'95',statLabel:d.statLabel||'z'};}\n");

        // Ordered fit-stat rows present in any model (label,key)
        js.append("function _spkPubStatDefs(){return [['Observations','n'],['R-squared','r2'],['Adjusted R-squared','r2_a'],")
          .append("['Pseudo R-squared','r2_p'],['F-statistic','F'],['Chi-squared','chi2'],['Log-likelihood','ll'],['RMSE','rmse']];}\n");

        // ---- render sortable grid (compact: b<stars> over (se)) ----
        js.append("window._spkPubSort={col:-1,dir:0};\n");
        // t2j fix3: the coefficient grid and the goodness-of-fit footer are two separate
        // <table> elements. With auto layout each sized its columns from its own content, so
        // the wider stat labels (e.g. "Adjusted R-squared") pushed the footer's number
        // columns out of line with the coefficient columns above. Give BOTH tables the SAME
        // fixed column geometry (table-layout:fixed + identical colgroup) so every column
        // lines up vertically regardless of nM.
        js.append("function _spkPubCols(n){n=n||1;var g='<colgroup><col style=\"width:220px\">';for(var i=0;i<n;i++)g+='<col style=\"width:130px\">';return g+'</colgroup>';}\n");
        js.append("function _spkRenderPub(){var el=document.getElementById('spkPubTable');if(!el)return;var M=_spkPubModel();if(!M){return;}")
          .append("var dark=document.documentElement&&document.documentElement.getAttribute&&document.documentElement.getAttribute('data-theme')==='dark';")
          .append("var s=window._spkPubSort;var sorted=(s.col>=0&&s.dir!==0);")
          .append("var rows=M.terms.slice();")
          .append("if(sorted){rows.sort(function(a,b){var A,B;if(s.col===-2){A=a.disp.toLowerCase();B=b.disp.toLowerCase();return A<B?-1*s.dir:A>B?1*s.dir:0;}")
          .append("var ca=a.cells[s.col],cb=b.cells[s.col];A=ca?parseFloat(ca.coef):NaN;B=cb?parseFloat(cb.coef):NaN;")
          .append("if(isNaN(A)&&isNaN(B))return 0;if(isNaN(A))return 1;if(isNaN(B))return -1;return (A-B)*s.dir;});}\n")
          .append("var arrow=function(c){if(s.col!==c||s.dir===0)return '';return s.dir>0?' \\u25b2':' \\u25bc';};")
          .append("var _inr=document.getElementById('spkPubInner');if(_inr)_inr.style.minWidth=(220+M.nM*130)+'px';")
          .append("var h='<table class=\"spk-pubgrid\" style=\"width:100%;table-layout:fixed;border-collapse:collapse;font-size:.82rem;\">';")
          .append("h+=_spkPubCols(M.nM);")
          .append("h+='<thead><tr>';")
          .append("h+='<th onclick=\"_spkPubSortBy(-2)\" style=\"cursor:pointer;text-align:left;padding:.45rem .6rem;border-bottom:2px solid #999;font-size:.72rem;text-transform:uppercase;letter-spacing:.04em;\">Term'+arrow(-2)+'</th>';")
          .append("for(var c=0;c<M.nM;c++){var ch=M.cols[c].num+(M.cols[c].label?'<br><span style=\"font-weight:400;opacity:.8;\">'+_spkH(M.cols[c].label)+'</span>':'');")
          .append("h+='<th onclick=\"_spkPubSortBy('+c+')\" style=\"cursor:pointer;text-align:right;padding:.45rem .6rem;border-bottom:2px solid #999;font-size:.72rem;\">'+ch+arrow(c)+'</th>';}")
          .append("h+='</tr></thead><tbody>';")
          // heading map by term name (only in natural order)
          .append("var hByName=sorted?{}:M.headingByName;")
          .append("for(var i=0;i<rows.length;i++){var t=rows[i];")
          .append("if(hByName[t.name]!==undefined){h+='<tr><td colspan=\"'+(M.nM+1)+'\" style=\"padding:.5rem .6rem .15rem;font-style:italic;font-weight:600;\">'+_spkH(hByName[t.name])+'</td></tr>';}")
          .append("h+='<tr>';h+='<td style=\"text-align:left;padding:.3rem .6rem;\">'+_spkH(t.disp)+'</td>';")
          .append("for(var c2=0;c2<M.nM;c2++){var cc=t.cells[c2];if(cc){var sub=cc.se;if(M.showT&&cc.tz)sub=(sub?sub+' ':'')+'['+cc.tz+']';if(M.showCI&&cc.lo&&cc.hi)sub=(sub?sub+' ':'')+'['+cc.lo+', '+cc.hi+']';")
          .append("h+='<td style=\"text-align:right;padding:.3rem .6rem;white-space:nowrap;\">'+_spkH(cc.coef)+'<sup>'+_spkH(cc.sig||'')+'</sup>'+(sub?'<br><span style=\"opacity:.7;font-size:.75rem;\">('+_spkH(sub)+')</span>':'')+'</td>';}")
          .append("else{h+='<td style=\"text-align:right;padding:.3rem .6rem;color:#999;\"></td>';}}h+='</tr>';}")
          // base rows (natural order only)
          .append("if(!sorted&&M.bases&&M.bases.length){for(var bi=0;bi<M.bases.length;bi++){h+='<tr><td style=\"text-align:left;padding:.25rem .6rem;color:#999;\">'+_spkH(M.bases[bi])+'</td>';for(var bc=0;bc<M.nM;bc++)h+='<td style=\"text-align:right;padding:.25rem .6rem;color:#999;font-style:italic;\">'+(bc===0?'(base)':'')+'</td>';h+='</tr>';}}")
          .append("h+='</tbody></table>';el.innerHTML=h;_spkRenderPubFooter(M);}\n");

        // ---- footer: indicate() rows, N, fit stats, star legend ----
        js.append("function _spkRenderPubFooter(M){var el=document.getElementById('spkPubFooter');if(!el||M.noFooter)return;")
          .append("var h='<table style=\"width:100%;table-layout:fixed;border-collapse:collapse;font-size:.8rem;border-top:2px solid #999;\">';")
          .append("h+=_spkPubCols(M.nM);")
          // indicate() indicator rows
          .append("for(var i=0;i<M.indicators.length;i++){var ind=M.indicators[i];h+='<tr><td style=\"text-align:left;padding:.25rem .6rem;\">'+_spkH(ind.label||'')+'</td>';for(var c=0;c<M.nM;c++)h+='<td style=\"text-align:right;padding:.25rem .6rem;\">'+_spkH((ind.vals&&ind.vals[c])||'')+'</td>';h+='</tr>';}")
          // stat rows present in any model
          .append("var defs=_spkPubStatDefs();for(var d2=0;d2<defs.length;d2++){var lab=defs[d2][0],key=defs[d2][1];var any=false;var vals=[];for(var c2=0;c2<M.nM;c2++){var v=(key==='n')?(M.models[c2].n||''):((M.models[c2].stats&&M.models[c2].stats[key])||'');if(v)any=true;vals.push(v);}")
          .append("if(any){h+='<tr><td style=\"text-align:left;padding:.25rem .6rem;'+(key==='n'?'border-top:1px solid #ccc;':'')+'\">'+lab+'</td>';for(var c3=0;c3<M.nM;c3++)h+='<td style=\"text-align:right;padding:.25rem .6rem;'+(key==='n'?'border-top:1px solid #ccc;':'')+'\">'+_spkH(vals[c3])+'</td>';h+='</tr>';}}")
          // addstats() custom rows
          .append("for(var a=0;a<M.addstats.length;a++){var ad=M.addstats[a];h+='<tr><td style=\"text-align:left;padding:.25rem .6rem;\">'+_spkH(ad.label||'')+'</td>';for(var c4=0;c4<M.nM;c4++)h+='<td style=\"text-align:right;padding:.25rem .6rem;\">'+_spkH((ad.vals&&ad.vals[c4])||'')+'</td>';h+='</tr>';}")
          .append("h+='</table>';")
          .append("var notes='Standard errors in parentheses'+(M.showT?'; ['+M.statLabel+']':'')+(M.showCI?'; ['+M.ciLevel+'% CI]':'')+'.';")
          .append("if(!M.noStars&&M.starLegend)notes+=' '+M.starLegend;")
          .append("h+='<p style=\"font-size:.73rem;opacity:.75;margin:.4rem .2rem 0;font-style:italic;\">'+_spkH(notes)+'</p>';")
          .append("el.innerHTML=h;}\n");

        js.append("function _spkPubSortBy(c){var s=window._spkPubSort;if(s.col!==c){s.col=c;s.dir=(c===-2?1:-1);}else{s.dir=(s.dir===0?(c===-2?1:-1):(s.dir>0?-1:(s.dir<0?0:1)));if(s.dir===0)s.col=-1;}_spkRenderPub();}\n");

        // ---- exports (coef) ----
        js.append(coefExportJs());
        // dispatcher
        js.append("function _spkPubExport(fmt){if(fmt==='copy-md')_spkPubCopy(_spkPubMarkdown());")
          .append("else if(fmt==='copy-latex')_spkPubCopy(_spkPubLatex(false));")
          .append("else if(fmt==='copy-tsv')_spkPubCopy(_spkPubDelim('\\t'));")
          .append("else if(fmt==='dl-csv')_spkPubDl(_spkPubTidyCsv(),'coefficients_tidy.csv','text/csv;charset=utf-8');")
          .append("else if(fmt==='dl-tex')_spkPubDl(_spkPubLatex(true),'table.tex','text/x-tex;charset=utf-8');}\n");
        // render on load (guarded; runs immediately under js_check mock too)
        js.append("try{_spkRenderPub();}catch(e){}\n");
        js.append("if(document&&document.addEventListener)document.addEventListener('DOMContentLoaded',function(){try{_spkRenderPub();}catch(e){}});\n");
        return js.toString();
    }

    /** Coef export builders (Markdown, booktabs LaTeX, tidy CSV, TSV grid). */
    private String coefExportJs() {
        StringBuilder js = new StringBuilder();
        // Markdown (stacked SE beneath, headings bold, indicate/N/stats rows)
        js.append("function _spkPubMarkdown(){var M=_spkPubModel();if(!M)return '';var L=[];")
          .append("var head='| |';var al='|:--|';for(var c=0;c<M.nM;c++){head+=' '+(M.cols[c].num+(M.cols[c].label?' '+M.cols[c].label:''))+' |';al+='--:|';}")
          .append("L.push(head);L.push(al);")
          .append("var hByName=M.headingByName;")
          .append("for(var i=0;i<M.terms.length;i++){var t=M.terms[i];")
          .append("if(hByName[t.name]!==undefined){var hr='| **'+_spkPubMd(hByName[t.name])+'** |';for(var c2=0;c2<M.nM;c2++)hr+=' |';L.push(hr);}")
          .append("var r1='| '+_spkPubMd(t.disp)+' |';for(var c3=0;c3<M.nM;c3++){var cc=t.cells[c3];r1+=cc?(' '+_spkPubMd(cc.coef+(cc.sig||''))+' |'):' |';}L.push(r1);")
          .append("var r2='|  |';var any2=false;for(var c4=0;c4<M.nM;c4++){var cc2=t.cells[c4];var sub=cc2?cc2.se:'';if(sub)any2=true;r2+=sub?(' ('+_spkPubMd(sub)+') |'):' |';}if(any2)L.push(r2);")
          .append("if(M.showT){var r3='|  |';var a3=false;for(var c5=0;c5<M.nM;c5++){var cc3=t.cells[c5];var v=cc3&&cc3.tz?'['+cc3.tz+']':'';if(v)a3=true;r3+=' '+_spkPubMd(v)+' |';}if(a3)L.push(r3);}")
          .append("if(M.showCI){var r4='|  |';var a4=false;for(var c6=0;c6<M.nM;c6++){var cc4=t.cells[c6];var v2=(cc4&&cc4.lo&&cc4.hi)?'['+cc4.lo+', '+cc4.hi+']':'';if(v2)a4=true;r4+=' '+_spkPubMd(v2)+' |';}if(a4)L.push(r4);}}")
          // base rows
          .append("for(var b=0;b<M.bases.length;b++){var br='| '+_spkPubMd(M.bases[b])+' |';for(var bc=0;bc<M.nM;bc++)br+=(bc===0?' (base) |':' |');L.push(br);}")
          // indicate
          .append("for(var k=0;k<M.indicators.length;k++){var ind=M.indicators[k];var ir='| '+_spkPubMd(ind.label||'')+' |';for(var ci=0;ci<M.nM;ci++)ir+=' '+_spkPubMd((ind.vals&&ind.vals[ci])||'')+' |';L.push(ir);}")
          // N + stats
          .append("var defs=_spkPubStatDefs();for(var d2=0;d2<defs.length;d2++){var lab=defs[d2][0],key=defs[d2][1];var any=false;var vals=[];for(var cc5=0;cc5<M.nM;cc5++){var vv=(key==='n')?(M.models[cc5].n||''):((M.models[cc5].stats&&M.models[cc5].stats[key])||'');if(vv)any=true;vals.push(vv);}if(any){var sr='| '+lab+' |';for(var c7=0;c7<M.nM;c7++)sr+=' '+_spkPubMd(vals[c7])+' |';L.push(sr);}}")
          .append("for(var a=0;a<M.addstats.length;a++){var ad=M.addstats[a];var ar='| '+_spkPubMd(ad.label||'')+' |';for(var c8=0;c8<M.nM;c8++)ar+=' '+_spkPubMd((ad.vals&&ad.vals[c8])||'')+' |';L.push(ar);}")
          .append("var notes='Standard errors in parentheses.'+((!M.noStars&&M.starLegend)?' '+M.starLegend:'');")
          .append("L.push('');L.push('_'+notes+'_');return L.join('\\n')+'\\n';}\n");

        // booktabs LaTeX (threeparttable; standalone wrapper when full=true)
        js.append("function _spkPubLatex(full){var M=_spkPubModel();if(!M)return '';var L=[];")
          .append("if(full){L.push('\\\\documentclass[border=12pt]{standalone}');L.push('\\\\usepackage[utf8]{inputenc}');L.push('\\\\usepackage[T1]{fontenc}');L.push('\\\\usepackage{booktabs}');L.push('\\\\usepackage{threeparttable}');L.push('\\\\usepackage{amsmath}');L.push('\\\\newcommand{\\\\sym}[1]{\\\\ensuremath{^{#1}}}');L.push('\\\\begin{document}');}")
          .append("L.push('\\\\begin{threeparttable}');L.push('\\\\caption{Regression results}');L.push('\\\\label{tab:sparkta}');")
          .append("L.push('\\\\begin{tabular}{l*{'+M.nM+'}{c}}');L.push('\\\\toprule');")
          .append("var h1='';for(var c=0;c<M.nM;c++)h1+=' & \\\\multicolumn{1}{c}{'+M.cols[c].num+'}';L.push(h1+' \\\\\\\\');")
          .append("var anyL=false,h2='';for(var c2=0;c2<M.nM;c2++){if(M.cols[c2].label)anyL=true;h2+=' & \\\\multicolumn{1}{c}{'+_spkPubLx(M.cols[c2].label||'')+'}';}if(anyL)L.push(h2+' \\\\\\\\');")
          .append("L.push('\\\\midrule');")
          .append("var hByName=M.headingByName;")
          .append("for(var i=0;i<M.terms.length;i++){var t=M.terms[i];")
          .append("if(hByName[t.name]!==undefined)L.push('\\\\multicolumn{'+(M.nM+1)+'}{l}{\\\\textit{'+_spkPubLx(hByName[t.name])+'}} \\\\\\\\');")
          .append("var r1=_spkPubLx(t.disp);for(var c3=0;c3<M.nM;c3++){var cc=t.cells[c3];r1+=' & '+(cc?(cc.coef+(cc.sig?'\\\\sym{'+cc.sig+'}':'')):'');}L.push(r1+' \\\\\\\\');")
          .append("var r2='';var any2=false;for(var c4=0;c4<M.nM;c4++){var cc2=t.cells[c4];if(cc2&&cc2.se)any2=true;r2+=' & '+(cc2&&cc2.se?'('+cc2.se+')':'');}if(any2)L.push(r2+' \\\\\\\\');")
          .append("if(M.showT){var r3='';var a3=false;for(var c5=0;c5<M.nM;c5++){var cc3=t.cells[c5];if(cc3&&cc3.tz)a3=true;r3+=' & '+(cc3&&cc3.tz?'['+cc3.tz+']':'');}if(a3)L.push(r3+' \\\\\\\\');}")
          .append("if(M.showCI){var r4='';var a4=false;for(var c6=0;c6<M.nM;c6++){var cc4=t.cells[c6];if(cc4&&cc4.lo&&cc4.hi)a4=true;r4+=' & '+(cc4&&cc4.lo&&cc4.hi?'['+cc4.lo+', '+cc4.hi+']':'');}if(a4)L.push(r4+' \\\\\\\\');}}")
          .append("for(var b=0;b<M.bases.length;b++){var br=_spkPubLx(M.bases[b]);for(var bc=0;bc<M.nM;bc++)br+=' & '+(bc===0?'(base)':'');L.push(br+' \\\\\\\\');}")
          .append("if(M.indicators.length){L.push('\\\\midrule');for(var k=0;k<M.indicators.length;k++){var ind=M.indicators[k];var ir=_spkPubLx(ind.label||'');for(var ci=0;ci<M.nM;ci++)ir+=' & '+_spkPubLx((ind.vals&&ind.vals[ci])||'');L.push(ir+' \\\\\\\\');}}")
          .append("L.push('\\\\midrule');")
          .append("var defs=_spkPubStatDefs();for(var d2=0;d2<defs.length;d2++){var lab=defs[d2][0],key=defs[d2][1];var any=false;var vals=[];for(var cc5=0;cc5<M.nM;cc5++){var vv=(key==='n')?(M.models[cc5].n||''):((M.models[cc5].stats&&M.models[cc5].stats[key])||'');if(vv)any=true;vals.push(vv);}if(any){var sr=(key==='r2'?'$R^2$':(key==='n'?'$N$':_spkPubLx(lab)));for(var c7=0;c7<M.nM;c7++)sr+=' & '+_spkPubLx(vals[c7]);L.push(sr+' \\\\\\\\');}}")
          .append("for(var a=0;a<M.addstats.length;a++){var ad=M.addstats[a];var ar=_spkPubLx(ad.label||'');for(var c8=0;c8<M.nM;c8++)ar+=' & '+_spkPubLx((ad.vals&&ad.vals[c8])||'');L.push(ar+' \\\\\\\\');}")
          .append("L.push('\\\\bottomrule');L.push('\\\\end{tabular}');")
          .append("L.push('\\\\begin{tablenotes}[flushleft]\\\\small');")
          .append("L.push('\\\\item Standard errors in parentheses.');")
          .append("if(!M.noStars&&M.starLegend){var lg=M.starLegend.replace(/</g,'$<$');L.push('\\\\item '+lg);}")
          .append("L.push('\\\\end{tablenotes}');L.push('\\\\end{threeparttable}');")
          .append("if(full)L.push('\\\\end{document}');")
          .append("return L.join('\\n')+'\\n';}\n");

        // tidy long CSV: model,term,b,se,ll,ul,p
        js.append("function _spkPubTidyCsv(){var M=_spkPubModel();if(!M)return '';var L=['model,term,b,se,ll,ul,p'];")
          .append("for(var m=0;m<M.nM;m++){for(var i=0;i<M.terms.length;i++){var cc=M.terms[i].cells[m];if(!cc)continue;")
          .append("L.push([_spkPubCsv(String(m+1)),_spkPubCsv(M.terms[i].disp),_spkPubCsv(cc.coef),_spkPubCsv(cc.se),_spkPubCsv(cc.lo),_spkPubCsv(cc.hi),_spkPubCsv(cc.p)].join(','));}}")
          .append("return L.join('\\n')+'\\n';}\n");

        // TSV grid (compact one row per term; for clipboard into a sheet)
        // t2j fix4: TSV/plain-text export. Each field is sheet-safe-guarded and stripped of
        // internal tabs/newlines BEFORE joining with the separator (the old code ran the
        // strip on the whole row after joining, which also flattened the tab separators).
        js.append("function _spkPubDelim(sep){var M=_spkPubModel();if(!M)return '';")
          .append("var clean=function(v){return _spkSheetSafe(String(v==null?'':v)).replace(/[\\t\\n\\r]/g,' ');};")
          .append("var L=[];var hd=clean('Term');for(var c=0;c<M.nM;c++)hd+=sep+clean(M.cols[c].num+(M.cols[c].label?' '+M.cols[c].label:''));L.push(hd);")
          .append("for(var i=0;i<M.terms.length;i++){var t=M.terms[i];var r=clean(t.disp);for(var c2=0;c2<M.nM;c2++){var cc=t.cells[c2];r+=sep+clean(cc?(cc.coef+(cc.sig||'')+(cc.se?' ('+cc.se+')':'')):'');}L.push(r);}")
          .append("return L.join('\\n')+'\\n';}\n");
        // shared tiny html escaper for on-page cells
        js.append("function _spkH(s){if(s==null)return '';return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}\n");
        return js.toString();
    }

    // =====================================================================
    // MARGINS JS
    // =====================================================================
    /** Emit window.__tblMargins from o.chart.mp* (rows=x levels, cols=series). */
    private String marginsDataJs() {
        String[] sData  = o.chart.mpData.isEmpty()  ? new String[]{""} : o.chart.mpData.split("~", -1);
        String[] sUpper = o.chart.mpUpper.isEmpty() ? new String[]{""} : o.chart.mpUpper.split("~", -1);
        String[] sLower = o.chart.mpLower.isEmpty() ? new String[]{""} : o.chart.mpLower.split("~", -1);
        String[] sXlab  = o.chart.mpXlab.isEmpty()  ? new String[]{""} : o.chart.mpXlab.split("~", -1);
        String mpSeriesRaw = (o.chart.mpSeries.isEmpty() || o.chart.mpSeries.equals("0")) ? "" : o.chart.mpSeries;
        String[] sNames = mpSeriesRaw.isEmpty() ? new String[]{""} : mpSeriesRaw.split("\\|", -1);
        int nSeries = sData.length;
        String[] xlabs = (sXlab.length > 0 ? sXlab[0] : "").split("\\|", -1);
        int nX = xlabs.length;
        String xTitle = o.axes.xtitle.isEmpty() ? "" : o.axes.xtitle;
        String yTitle = o.axes.ytitle.isEmpty() ? o.chart.mpYlab : o.axes.ytitle;
        String ciLevel = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;

        StringBuilder js = new StringBuilder();
        js.append("window.__tblMargins = {\n");
        js.append("  ylab: '").append(gen.escJs(yTitle)).append("',\n");
        js.append("  xlab: '").append(gen.escJs(xTitle)).append("',\n");
        js.append("  ciLevel: '").append(gen.escJs(ciLevel)).append("',\n");
        // series column labels
        js.append("  series: [");
        for (int s = 0; s < nSeries; s++) {
            String nm = (s < sNames.length) ? sNames[s].trim() : "";
            js.append(s > 0 ? ", " : "").append("'").append(gen.escJs(nm)).append("'");
        }
        js.append("],\n");
        // rows
        js.append("  rows: [\n");
        for (int r = 0; r < nX; r++) {
            js.append("    { label: '").append(gen.escJs(xlabs[r].trim())).append("', cells: [");
            for (int s = 0; s < nSeries; s++) {
                String[] dv = sData[s].split("\\|", -1);
                String[] lo = (s < sLower.length ? sLower[s] : "").split("\\|", -1);
                String[] hi = (s < sUpper.length ? sUpper[s] : "").split("\\|", -1);
                String m  = (r < dv.length) ? dv[r].trim() : "";
                String l  = (r < lo.length) ? lo[r].trim() : "";
                String u  = (r < hi.length) ? hi[r].trim() : "";
                js.append(s > 0 ? ", " : "")
                  .append("{m:'").append(gen.escJs(fmtMg(m))).append("', lo:'").append(gen.escJs(fmtMg(l)))
                  .append("', hi:'").append(gen.escJs(fmtMg(u))).append("'}");
            }
            js.append("] }").append(r < nX - 1 ? "," : "").append("\n");
        }
        js.append("  ]\n};\n");
        return js.toString();
    }

    /** Format a margins number to 4sf-ish (matches on-chart 4dp), blank for missing/dot. */
    private String fmtMg(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty() || t.equals(".")) return "";
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v)) return "";
            return String.format(java.util.Locale.ROOT, "%.4f", v);
        } catch (NumberFormatException e) { return t; }
    }

    private String marginsJs() {
        StringBuilder js = new StringBuilder();
        js.append("function _spkH(s){if(s==null)return '';return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}\n");
        js.append("window._spkPubSort={col:-1,dir:0};\n");
        // render margins table (rows = x levels, cols = series; cell = margin over [CI])
        js.append("function _spkRenderPub(){var el=document.getElementById('spkPubTable');if(!el)return;var M=window.__tblMargins;if(!M)return;")
          .append("var nS=M.series.length;var s=window._spkPubSort;var rows=M.rows.slice();")
          .append("if(s.col>=0&&s.dir!==0){rows.sort(function(a,b){var A,B;if(s.col===-2){A=(a.label||'').toLowerCase();B=(b.label||'').toLowerCase();return A<B?-s.dir:A>B?s.dir:0;}A=parseFloat(a.cells[s.col]?a.cells[s.col].m:NaN);B=parseFloat(b.cells[s.col]?b.cells[s.col].m:NaN);if(isNaN(A)&&isNaN(B))return 0;if(isNaN(A))return 1;if(isNaN(B))return -1;return (A-B)*s.dir;});}")
          .append("var arrow=function(c){if(s.col!==c||s.dir===0)return '';return s.dir>0?' \\u25b2':' \\u25bc';};")
          .append("var singleBlank=(nS===1&&(!M.series[0]));")
          .append("var h='<table class=\"spk-pubgrid\" style=\"width:100%;border-collapse:collapse;font-size:.82rem;\"><thead>';")
          .append("if(!singleBlank){h+='<tr><th></th>';for(var c=0;c<nS;c++)h+='<th style=\"text-align:center;padding:.35rem .6rem;font-size:.72rem;\">'+_spkH(M.series[c])+'</th>';h+='</tr>';}")
          .append("h+='<tr><th onclick=\"_spkPubSortBy(-2)\" style=\"cursor:pointer;text-align:left;padding:.35rem .6rem;border-bottom:2px solid #999;font-size:.72rem;text-transform:uppercase;\">'+_spkH(M.xlab||'')+arrow(-2)+'</th>';")
          .append("for(var c2=0;c2<nS;c2++)h+='<th onclick=\"_spkPubSortBy('+c2+')\" style=\"cursor:pointer;text-align:right;padding:.35rem .6rem;border-bottom:2px solid #999;font-size:.72rem;\">'+_spkH(M.ylab||'Margin')+arrow(c2)+'</th>';")
          .append("h+='</tr></thead><tbody>';")
          .append("for(var i=0;i<rows.length;i++){var rw=rows[i];h+='<tr><td style=\"text-align:left;padding:.3rem .6rem;\">'+_spkH(rw.label)+'</td>';")
          .append("for(var c3=0;c3<nS;c3++){var cc=rw.cells[c3];if(cc&&cc.m){var ci=(cc.lo&&cc.hi)?'<br><span style=\"opacity:.7;font-size:.75rem;\">['+_spkH(cc.lo)+', '+_spkH(cc.hi)+']</span>':'';h+='<td style=\"text-align:right;padding:.3rem .6rem;white-space:nowrap;\">'+_spkH(cc.m)+ci+'</td>';}else{h+='<td style=\"text-align:right;padding:.3rem .6rem;color:#999;\">.</td>';}}h+='</tr>';}")
          .append("h+='</tbody></table>';el.innerHTML=h;_spkRenderPubFooter(M);}\n");
        js.append("function _spkRenderPubFooter(M){var el=document.getElementById('spkPubFooter');if(!el)return;")
          .append("var notes='Predictive margins with '+(M.ciLevel||'95')+'% CI in brackets.';")
          .append("el.innerHTML='<p style=\"font-size:.73rem;opacity:.75;margin:.4rem .2rem 0;font-style:italic;border-top:2px solid #999;padding-top:.3rem;\">'+_spkH(notes)+'</p>';}\n");
        js.append("function _spkPubSortBy(c){var s=window._spkPubSort;if(s.col!==c){s.col=c;s.dir=(c===-2?1:-1);}else{s.dir=(s.dir===0?1:(s.dir>0?-1:0));if(s.dir===0)s.col=-1;}_spkRenderPub();}\n");
        // margins exports
        js.append("function _spkMgTitleCols(M){var nS=M.series.length;return nS;}\n");
        js.append("function _spkPubMarkdown(){var M=window.__tblMargins;if(!M)return '';var nS=M.series.length;var L=[];")
          .append("var h='| '+_spkPubMd(M.xlab||'')+' |';var al='|:--|';for(var c=0;c<nS;c++){h+=' '+_spkPubMd(M.series[c]||(M.ylab||'Margin'))+' |';al+='--:|';}L.push(h);L.push(al);")
          .append("for(var i=0;i<M.rows.length;i++){var rw=M.rows[i];var r='| '+_spkPubMd(rw.label)+' |';for(var c2=0;c2<nS;c2++){var cc=rw.cells[c2];r+=cc&&cc.m?(' '+_spkPubMd(cc.m)+((cc.lo&&cc.hi)?' ['+cc.lo+', '+cc.hi+']':'')+' |'):' . |';}L.push(r);}")
          .append("L.push('');L.push('_Predictive margins with '+(M.ciLevel||'95')+'% CI in brackets._');return L.join('\\n')+'\\n';}\n");
        js.append("function _spkPubLatex(full){var M=window.__tblMargins;if(!M)return '';var nS=M.series.length;var L=[];")
          .append("if(full){L.push('\\\\documentclass[border=12pt]{standalone}');L.push('\\\\usepackage[utf8]{inputenc}');L.push('\\\\usepackage[T1]{fontenc}');L.push('\\\\usepackage{booktabs}');L.push('\\\\usepackage{threeparttable}');L.push('\\\\usepackage{amsmath}');L.push('\\\\begin{document}');}")
          .append("L.push('\\\\begin{threeparttable}');L.push('\\\\caption{Predictive margins}');L.push('\\\\label{tab:sparkta_margins}');")
          .append("L.push('\\\\begin{tabular}{l*{'+nS+'}{c}}');L.push('\\\\toprule');")
          .append("var h='';for(var c=0;c<nS;c++)h+=' & \\\\multicolumn{1}{c}{'+_spkPubLx(M.series[c]||(M.ylab||'Margin'))+'}';L.push(_spkPubLx(M.xlab||'')+h+' \\\\\\\\');L.push('\\\\midrule');")
          .append("for(var i=0;i<M.rows.length;i++){var rw=M.rows[i];var r=_spkPubLx(rw.label);for(var c2=0;c2<nS;c2++){var cc=rw.cells[c2];r+=' & '+(cc&&cc.m?_spkPubLx(cc.m):'.');}L.push(r+' \\\\\\\\');")
          .append("var r2='';var any=false;for(var c3=0;c3<nS;c3++){var cc2=rw.cells[c3];if(cc2&&cc2.lo&&cc2.hi)any=true;r2+=' & '+((cc2&&cc2.lo&&cc2.hi)?'['+cc2.lo+', '+cc2.hi+']':'');}if(any)L.push(r2+' \\\\\\\\');}")
          .append("L.push('\\\\bottomrule');L.push('\\\\end{tabular}');")
          .append("L.push('\\\\begin{tablenotes}[flushleft]\\\\small');L.push('\\\\item Predictive margins; '+(M.ciLevel||'95')+'\\\\% CI in brackets.');L.push('\\\\end{tablenotes}');")
          .append("L.push('\\\\end{threeparttable}');if(full)L.push('\\\\end{document}');return L.join('\\n')+'\\n';}\n");
        js.append("function _spkPubTidyCsv(){var M=window.__tblMargins;if(!M)return '';var L=['series,level,margin,ll,ul'];")
          .append("for(var c=0;c<M.series.length;c++){for(var i=0;i<M.rows.length;i++){var cc=M.rows[i].cells[c];if(!cc||!cc.m)continue;L.push([_spkPubCsv(M.series[c]||('s'+(c+1))),_spkPubCsv(M.rows[i].label),_spkPubCsv(cc.m),_spkPubCsv(cc.lo),_spkPubCsv(cc.hi)].join(','));}}return L.join('\\n')+'\\n';}\n");
        js.append("function _spkPubExport(fmt){if(fmt==='copy-md')_spkPubCopy(_spkPubMarkdown());")
          .append("else if(fmt==='copy-latex')_spkPubCopy(_spkPubLatex(false));")
          .append("else if(fmt==='copy-tsv')_spkPubCopy(_spkPubMarkdown());")
          .append("else if(fmt==='dl-csv')_spkPubDl(_spkPubTidyCsv(),'margins_tidy.csv','text/csv;charset=utf-8');")
          .append("else if(fmt==='dl-tex')_spkPubDl(_spkPubLatex(true),'margins_table.tex','text/x-tex;charset=utf-8');}\n");
        js.append("try{_spkRenderPub();}catch(e){}\n");
        js.append("if(document&&document.addEventListener)document.addEventListener('DOMContentLoaded',function(){try{_spkRenderPub();}catch(e){}});\n");
        return js.toString();
    }
}
