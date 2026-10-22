// amazon-to-ai/index.html の変換ロジックのコピー。変更時は両方そろえること。
function amazonExtract(doc, pageUrl) {
  var clean = function (s) { return (s || "").replace(/[‎‏ ]/g, " ").replace(/\s+/g, " ").trim(); };
  var $ = function (sel, root) { return (root || doc).querySelector(sel); };
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || doc).querySelectorAll(sel)); };
  // script/style の中身（Amazonのページに大量に埋め込まれている）を除いて文字だけを集める
  var textOf = function (el) {
    if (!el) return "";
    var out = [];
    (function walk(n) {
      if (n.nodeType === 3) { out.push(n.nodeValue); return; }
      if (n.nodeType !== 1 || /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE)$/.test(n.nodeName)) return;
      for (var c = n.firstChild; c; c = c.nextSibling) walk(c);
      if (/^(P|DIV|BR|LI)$/.test(n.nodeName)) out.push(" ");
    })(el);
    return clean(out.join(""));
  };
  var txt = function (sel, root) { return textOf($(sel, root)); };
  var first = function (sels, root) { for (var i = 0; i < sels.length; i++) { var t = txt(sels[i], root); if (t) return t; } return ""; };
  // 「5つ星のうち4.2」「4.2 out of 5 stars」どちらの表記からも評価値を取り出す
  var star = function (s) { s = (s || "").replace(/,/g, "."); var m = s.match(/5\s*つ星のうち\s*([\d.]+)/) || s.match(/([\d.]+)\s*out of/) || s.match(/[\d.]+/); return m ? (m[1] || m[0]) : ""; };
  // 星アイコンのクラス名（a-star-4 / a-star-4-5）からも評価値を読めるようにする
  var starOf = function (root, sels) {
    for (var i = 0; i < sels.length; i++) {
      var el = $(sels[i], root);
      if (!el) continue;
      var alt = textOf(el.matches(".a-icon-alt") ? el : $(".a-icon-alt", el) || el);
      if (alt && /[\d]/.test(alt)) return star(alt);
      var icon = el.closest("i") || $("i", el) || el;
      var cm = (icon.className || "").match(/a-star-(?:small-|mini-|medium-)?(\d)(?:-(\d))?/);
      if (cm) return cm[2] ? cm[1] + "." + cm[2] : cm[1] + ".0";
    }
    return "";
  };
  var abs = function (href) { try { return new URL(href, pageUrl || "https://www.amazon.co.jp/").href; } catch (e) { return href || ""; } };

  var canonical = ($('link[rel="canonical"]') || {}).href || "";
  var url = pageUrl || canonical || "";
  var asinMatch = (url + " " + canonical).match(/\/(?:dp|gp\/product)\/([A-Z0-9]{10})/);
  var asinInput = $('input#ASIN, input[name="ASIN"]');

  // 検索結果ページ
  var results = $$('div[data-component-type="s-search-result"]');
  if (results.length) {
    var items = results.map(function (r, i) {
      var link = $("h2 a, a.a-link-normal.s-no-outline, a.a-link-normal", r);
      var href = link ? link.getAttribute("href") : "";
      var asin = r.getAttribute("data-asin") || "";
      return {
        rank: i + 1,
        asin: asin,
        title: first(["h2 span", "h2"], r),
        price: first([".a-price:not(.a-text-price) .a-offscreen", ".a-price .a-offscreen"], r),
        listPrice: txt(".a-price.a-text-price .a-offscreen", r),
        rating: star(txt("i.a-icon-star-small .a-icon-alt, i.a-icon-star-mini .a-icon-alt, .a-icon-alt", r)),
        reviewCount: first(['[aria-label$="件の評価"]', 'span.s-underline-text', 'a[href*="customerReviews"] span'], r).replace(/[()]/g, ""),
        sponsored: /スポンサー|Sponsored/.test(r.textContent),
        prime: !!$("i.a-icon-prime, [aria-label='Amazon Prime']", r),
        url: asin ? abs("/dp/" + asin) : abs(href)
      };
    }).filter(function (x) { return x.title; });
    return {
      pageType: "search",
      query: (($('input#twotabsearchtextbox') || {}).value || "").trim(),
      url: url,
      resultCount: items.length,
      results: items
    };
  }

  // 商品ページ
  var title = first(["#productTitle", "#title", "#ebooksProductTitle"]);
  if (title) {
    var specs = {};
    var addSpec = function (k, v) { k = clean(k).replace(/\s*[:：]\s*$/, ""); v = clean(v); if (k && v && !specs[k] && k.length < 60) specs[k] = v; };
    $$("#productDetails_techSpec_section_1 tr, #productDetails_detailBullets_sections1 tr, #productOverview_feature_div tr, #poExpander tr, table.prodDetTable tr, #technicalSpecifications_section_1 tr").forEach(function (tr) {
      addSpec(txt("th, td:first-child", tr), txt("td:last-child", tr));
    });
    $$("#detailBullets_feature_div li, #detailBulletsWrapper_feature_div li").forEach(function (li) {
      var spans = $$("span.a-text-bold", li);
      if (spans.length) { var k = clean(spans[0].textContent); addSpec(k, clean(li.textContent).replace(k, "")); }
    });
    delete specs["おすすめ度"]; delete specs["カスタマーレビュー"]; delete specs["Customer Reviews"]; delete specs["Amazon 売れ筋ランキング"]; delete specs["Best Sellers Rank"];

    var rankText = "";
    $$("#detailBullets_feature_div li, #productDetails_detailBullets_sections1 tr, #SalesRank").forEach(function (el) {
      if (/売れ筋ランキング|Best Sellers Rank/.test(el.textContent) && !rankText) rankText = clean(el.textContent).replace(/^.*?(売れ筋ランキング|Best Sellers Rank)\s*[:：]?\s*/, "").replace(/\(.*?(トップ|See Top).*?\)/g, "");
    });

    var images = [];
    var land = $("#landingImage, #imgBlkFront, #ebooksImgBlkFront");
    if (land) {
      var hi = land.getAttribute("data-old-hires");
      if (hi) images.push(hi);
      try { Object.keys(JSON.parse(land.getAttribute("data-a-dynamic-image") || "{}")).forEach(function (u) { if (images.length < 1) images.push(u); }); } catch (e) {}
      if (!images.length && land.getAttribute("src")) images.push(land.getAttribute("src"));
    }

    var ratingRaw = first(["#acrPopover .a-icon-alt", '[data-hook="rating-out-of-text"]', "#averageCustomerReviews .a-icon-alt"]);
    var ratingTitle = ($("#acrPopover") || { getAttribute: function () { return ""; } }).getAttribute("title") || "";

    var reviews = $$('[data-hook="review"], #cm-cr-dp-review-list > li, .review[id^="R"]').filter(function (rv, i, all) {
      return all.indexOf(rv.parentElement && rv.parentElement.closest('[data-hook="review"]')) < 0;
    }).slice(0, 20).map(function (rv) {
      var starSels = ['[data-hook="review-star-rating"]', '[data-hook="cmps-review-star-rating"]', 'i[class*="a-star"]', ".a-icon-alt"];
      var rt = $('[data-hook="review-title"]', rv);
      var title = "";
      if (rt) {
        var t = rt.cloneNode(true);
        Array.prototype.slice.call(t.querySelectorAll("i, .a-icon-alt, .a-letter-space")).forEach(function (x) { x.remove(); });
        title = textOf(t);
      }
      var author = txt(".a-profile-name", rv);
      var date = txt('[data-hook="review-date"]', rv);
      var body = first(['[data-hook="review-body"] [data-hook="review-collapsed"]', '[data-hook="review-body"]', ".review-text-content", ".review-text", '[data-hook="review-collapsed"]'], rv);
      if (!body) {
        // 構造が変わっていても、レビュー内で一番長い文章を本文として拾う
        $$("span, div", rv).forEach(function (el) {
          if (el.children.length > 3) return;
          var t2 = textOf(el);
          if (t2.length > body.length && t2.length > 15 && t2 !== title && t2 !== date && t2.indexOf(author + " ") !== 0 && !/つ星のうち|out of 5|役に立った|helpful|レビュー済み|Reviewed in/.test(t2)) body = t2;
        });
      }
      return {
        author: author,
        rating: starOf(rv, starSels),
        title: title,
        date: date,
        verified: !!$('[data-hook="avp-badge"], [data-hook="avp-badge-linkless"]', rv),
        body: body.replace(/\s*(続きを読む|Read more|もっと見る)\s*$/, "")
      };
    });
    var rating = star(ratingRaw || ratingTitle);

    var asin = (asinInput && asinInput.value) || (asinMatch && asinMatch[1]) || specs["ASIN"] || "";
    return {
      pageType: "product",
      url: asin ? abs("/dp/" + asin) : url,
      asin: asin,
      title: title,
      brand: first(["#bylineInfo", "#brand"]).replace(/^(ブランド|Brand)\s*[:：]\s*|^Visit the |のストアを表示$|\s*Store$/g, ""),
      categories: $$("#wayfinding-breadcrumbs_feature_div li a").map(function (a) { return clean(a.textContent); }).filter(Boolean),
      price: first(["#corePrice_feature_div .a-price .a-offscreen", "#corePriceDisplay_desktop_feature_div .a-price:not(.a-text-price) .a-offscreen", "#apex_desktop .a-price .a-offscreen", "#priceblock_ourprice", "#priceblock_dealprice", "#kindle-price", ".a-price .a-offscreen"]),
      listPrice: first(["#corePriceDisplay_desktop_feature_div .a-price.a-text-price .a-offscreen", ".basisPrice .a-offscreen"]),
      availability: txt("#availability"),
      seller: first(["#merchantInfoFeature_feature_div .offer-display-feature-text-message", "#sellerProfileTriggerId", "#merchant-info"]),
      rating: rating,
      reviewCount: txt("#acrCustomerReviewText").replace(/[()]/g, ""),
      salesRank: rankText,
      features: $$("#feature-bullets li, #featurebullets_feature_div li").map(function (li) { return clean(li.textContent); }).filter(function (s) { return s && !/^(この商品について|›)/.test(s); }),
      description: first(["#productDescription", "#bookDescription_feature_div .a-expander-content", "#bookDescription_feature_div"]),
      specs: specs,
      images: images,
      reviews: reviews
    };
  }

  // その他のページ：本文だけを抽出
  var body = doc.body ? doc.body.cloneNode(true) : doc.createElement("div");
  $$("script, style, noscript, iframe, svg, header, footer, nav, #navbar, #nav-main, #navFooter, [aria-hidden='true']", body).forEach(function (el) { el.remove(); });
  return { pageType: "other", url: url, title: clean((doc.querySelector("title") || {}).textContent), text: clean(body.textContent).slice(0, 20000) };
}

function amazonToMarkdown(d) {
  var L = [];
  var esc = function (s) { return String(s).replace(/\|/g, "\\|"); };
  if (d.pageType === "search") {
    L.push("# Amazon検索結果" + (d.query ? "：「" + d.query + "」" : ""));
    if (d.url) L.push("", "URL: " + d.url);
    L.push("", "件数: " + d.resultCount, "", "| # | 商品名 | 価格 | 評価 | レビュー数 | ASIN | 備考 |", "|---|---|---|---|---|---|---|");
    d.results.forEach(function (r) {
      var note = [r.sponsored ? "広告" : "", r.prime ? "Prime" : "", r.listPrice ? "参考価格 " + r.listPrice : ""].filter(Boolean).join(", ");
      L.push("| " + [r.rank, esc(r.title), r.price || "-", r.rating || "-", r.reviewCount || "-", r.asin || "-", note || ""].join(" | ") + " |");
    });
    return L.join("\n");
  }
  if (d.pageType === "product") {
    L.push("# " + d.title, "");
    var meta = [["ASIN", d.asin], ["URL", d.url], ["ブランド", d.brand], ["カテゴリ", d.categories.join(" > ")], ["価格", d.price], ["参考価格", d.listPrice], ["在庫", d.availability], ["販売元", d.seller], ["評価", d.rating ? d.rating + " / 5" : ""], ["評価数", d.reviewCount], ["売れ筋ランキング", d.salesRank]];
    meta.forEach(function (m) { if (m[1]) L.push("- **" + m[0] + "**: " + m[1]); });
    if (d.features.length) { L.push("", "## 商品の特徴"); d.features.forEach(function (f) { L.push("- " + f); }); }
    if (d.description) L.push("", "## 商品説明", d.description);
    var keys = Object.keys(d.specs);
    if (keys.length) { L.push("", "## 仕様・詳細", "| 項目 | 値 |", "|---|---|"); keys.forEach(function (k) { L.push("| " + esc(k) + " | " + esc(d.specs[k]) + " |"); }); }
    if (d.reviews.length) {
      L.push("", "## カスタマーレビュー（ページ上の" + d.reviews.length + "件）");
      d.reviews.forEach(function (r, i) {
        L.push("", "### " + (i + 1) + ". " + (r.rating ? "★" + r.rating + " " : "") + (r.title || "(無題)"));
        var by = [r.author, r.date, r.verified ? "Amazonで購入" : ""].filter(Boolean).join(" / ");
        if (by) L.push("_" + by + "_");
        if (r.body) L.push("", r.body);
      });
    }
    if (d.images.length) L.push("", "## 画像", d.images.map(function (u) { return "- " + u; }).join("\n"));
    return L.join("\n");
  }
  L.push("# " + (d.title || "ページ本文"));
  if (d.url) L.push("", "URL: " + d.url);
  L.push("", d.text);
  return L.join("\n");
}
