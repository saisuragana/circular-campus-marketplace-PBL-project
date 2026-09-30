/* ============================================================
   Circular Campus - front-end behaviour
   Plain JavaScript, no libraries. Everything here is progressive:
   if a script fails, the forms and links still work on their own.
   ============================================================ */
(function () {
  "use strict";

  var reduceMotion = window.matchMedia
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------------------------------------------------- toast */
  function toast(message, bad) {
    var el = document.getElementById("toast");
    if (!el) return;
    el.textContent = message;
    el.className = "toast is-on" + (bad ? " is-bad" : "");
    clearTimeout(el._timer);
    el._timer = setTimeout(function () { el.className = "toast"; }, 2600);
  }

  /* ---------------------------------------------------- navbar */
  var navToggle = document.getElementById("navToggle");
  var navLinks = document.getElementById("navLinks");
  if (navToggle && navLinks) {
    navToggle.addEventListener("click", function () {
      var open = navLinks.classList.toggle("is-open");
      navToggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
  }

  var walletChip = document.getElementById("walletChip");
  if (walletChip) {
    var toggleWallet = function () {
      var open = walletChip.classList.toggle("is-open");
      walletChip.setAttribute("aria-expanded", open ? "true" : "false");
    };
    walletChip.addEventListener("click", function (e) {
      if (e.target.tagName !== "A") toggleWallet();
    });
    walletChip.addEventListener("keydown", function (e) {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleWallet(); }
    });
    document.addEventListener("click", function (e) {
      if (!walletChip.contains(e.target)) {
        walletChip.classList.remove("is-open");
        walletChip.setAttribute("aria-expanded", "false");
      }
    });
  }

  /* ------------------------------------------- add to cart (fetch) */
  function setBadge(count) {
    var badge = document.getElementById("cartBadge");
    if (!badge) return;
    badge.textContent = count;
    if (count > 0) badge.removeAttribute("hidden");
    else badge.setAttribute("hidden", "hidden");
  }

  document.addEventListener("submit", function (e) {
    var form = e.target;
    if (!form.classList.contains("add-form")) return;   // other forms post normally
    if (!window.fetch) return;

    e.preventDefault();
    var button = form.querySelector(".add-btn");
    var original = button ? button.textContent : "";
    if (button) { button.textContent = "Adding..."; button.classList.add("is-loading"); button.disabled = true; }

    fetch(form.getAttribute("action"), {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Requested-With": "fetch",
        "Accept": "application/json"
      },
      body: new URLSearchParams(new FormData(form)).toString(),
      credentials: "same-origin"
    })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        setBadge(data.count);
        toast(data.message, !data.ok);
        if (button) {
          button.classList.remove("is-loading");
          if (data.ok) {
            button.textContent = "Added \u2713";
            button.classList.add("is-done");
            setTimeout(function () {
              button.textContent = original;
              button.classList.remove("is-done");
              button.disabled = false;
            }, 1600);
          } else {
            button.textContent = original;
            button.disabled = false;
          }
        }
      })
      .catch(function () {
        if (button) {
          button.textContent = original;
          button.classList.remove("is-loading");
          button.disabled = false;
        }
        form.submit();     // fall back to a normal POST
      });
  });

  /* ---------------------------------------------------- reveal */
  var revealables = document.querySelectorAll(".reveal");
  if (revealables.length) {
    if (reduceMotion || !("IntersectionObserver" in window)) {
      for (var i = 0; i < revealables.length; i++) revealables[i].classList.add("is-in");
    } else {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            io.unobserve(entry.target);
          }
        });
      }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
      for (var j = 0; j < revealables.length; j++) io.observe(revealables[j]);
    }
  }

  /* ------------------------------------------- hero parallax + tilt */
  var stage = document.getElementById("heroStage");
  if (stage && !reduceMotion && window.matchMedia("(pointer: fine)").matches) {
    var orbit = stage.querySelector(".orbit");
    stage.addEventListener("mousemove", function (e) {
      var box = stage.getBoundingClientRect();
      var x = (e.clientX - box.left) / box.width - 0.5;
      var y = (e.clientY - box.top) / box.height - 0.5;
      orbit.style.setProperty("--rx", (x * 14).toFixed(2) + "deg");
      orbit.style.setProperty("--ry", (-y * 12).toFixed(2) + "deg");
    });
    stage.addEventListener("mouseleave", function () {
      orbit.style.setProperty("--rx", "0deg");
      orbit.style.setProperty("--ry", "0deg");
    });
  }

  var tilts = document.querySelectorAll(".tilt");
  if (!reduceMotion) {
    Array.prototype.forEach.call(tilts, function (el) {
      el.addEventListener("mousemove", function (e) {
        var b = el.getBoundingClientRect();
        var x = (e.clientX - b.left) / b.width - 0.5;
        var y = (e.clientY - b.top) / b.height - 0.5;
        el.style.transform = "perspective(800px) rotateY(" + (x * 10).toFixed(2)
          + "deg) rotateX(" + (-y * 10).toFixed(2) + "deg)";
      });
      el.addEventListener("mouseleave", function () { el.style.transform = ""; });
    });
  }

  /* ------------------------------------- messages from the query string */
  var msgBox = document.getElementById("formMsg");
  if (msgBox) {
    var params = new URLSearchParams(window.location.search);
    var text = null, good = false;
    if (params.get("error")) {
      text = params.get("error");
    } else if (params.get("created")) {
      text = "Account created. Log in with your new details."; good = true;
    } else if (params.get("loggedout")) {
      text = "You are logged out. Your cart is saved for next time."; good = true;
    } else if (params.get("msg")) {
      text = params.get("msg");
    }
    if (text) {
      msgBox.textContent = text;
      msgBox.className = "form-msg" + (good ? " ok" : "");
      msgBox.removeAttribute("hidden");
    }
    var emailField = document.getElementById("email");
    if (emailField && params.get("email")) emailField.value = params.get("email");
    var nameField = document.getElementById("name");
    if (nameField && params.get("name")) nameField.value = params.get("name");
  }

  /* ------------------------------- create listing: preview + suggestions */
  var sellForm = document.getElementById("sellForm");
  if (sellForm) {
    /* form.title would return the element's title attribute, not the input,
       so every field is read through form.elements[name]. */
    function f(name) { return sellForm.elements[name]; }

    var pTitle = document.getElementById("pvTitle");
    var pPrice = document.getElementById("pvPrice");
    var pCat = document.getElementById("pvCat");
    var pCond = document.getElementById("pvCond");
    var pQty = document.getElementById("pvQty");

    function paint() {
      var t = f("title").value.trim();
      var p = parseFloat(f("price").value);
      pTitle.textContent = t || "Your item title";
      pPrice.textContent = "\u20B9" + (isFinite(p) && p > 0 ? p.toFixed(2) : "0.00");
      pCat.textContent = f("category").value || "Category";
      pCond.textContent = f("condition").value || "Condition";
      pQty.textContent = (f("quantity").value || "1") + " available";
    }
    ["input", "change"].forEach(function (evt) { sellForm.addEventListener(evt, paint); });
    paint();

    /* Smart Suggestions - asks our own Java servlet (/ai-suggest),
       which runs a local rule set. No external AI service is involved. */
    var suggestBox = document.getElementById("suggestList");
    var suggestNote = document.getElementById("suggestNote");
    var timer = null;

    f("title").addEventListener("input", function () {
      clearTimeout(timer);
      var value = f("title").value.trim();
      if (value.length < 3) {
        suggestBox.innerHTML = "";
        suggestNote.textContent = "Start typing a title and suggestions appear here.";
        return;
      }
      timer = setTimeout(function () { askSuggestions(value); }, 350);
    });

    function askSuggestions(title) {
      fetch("ai-suggest?title=" + encodeURIComponent(title), { credentials: "same-origin" })
        .then(function (r) { return r.json(); })
        .then(function (data) {
          suggestBox.innerHTML = "";
          suggestNote.textContent = data.matched
            ? "Matched on the words in your title."
            : "No rule matched yet. Here is a guess from your own words.";

          var catBtn = document.createElement("button");
          catBtn.type = "button";
          catBtn.className = "suggest-chip";
          catBtn.textContent = "Category: " + data.category;
          catBtn.addEventListener("click", function () {
            f("category").value = data.category;
            paint();
            toast("Category set to " + data.category);
          });
          suggestBox.appendChild(catBtn);

          data.keywords.forEach(function (k) {
            var b = document.createElement("button");
            b.type = "button";
            b.className = "suggest-chip";
            b.textContent = "+ " + k;
            b.addEventListener("click", function () {
              var current = f("keywords").value.split(",")
                .map(function (s) { return s.trim(); })
                .filter(function (s) { return s.length; });
              if (current.indexOf(k) === -1) current.push(k);
              f("keywords").value = current.join(", ");
            });
            suggestBox.appendChild(b);
          });
        })
        .catch(function () {
          suggestNote.textContent = "Suggestions are unavailable right now.";
        });
    }

    /* client-side checks - the servlet repeats all of them */
    sellForm.addEventListener("submit", function (e) {
      var problems = [];
      if (f("title").value.trim().length < 3) problems.push("a longer title");
      if (!f("category").value) problems.push("a category");
      if (!f("condition").value) problems.push("a condition");
      if (!(parseFloat(f("price").value) > 0)) problems.push("a price above zero");
      if (!(parseInt(f("quantity").value, 10) > 0)) problems.push("a quantity of at least 1");
      if (f("description").value.trim().length < 10) problems.push("a description of at least 10 characters");
      if (problems.length) {
        e.preventDefault();
        toast("Add " + problems.join(", ") + ".", true);
      }
    });
  }

  /* ------------------------------- create account: matching passwords */
  var signupForm = document.getElementById("signupForm");
  if (signupForm) {
    signupForm.addEventListener("submit", function (e) {
      var pw = signupForm.password.value;
      var cf = signupForm.confirmPassword.value;
      if (pw.length < 6) { e.preventDefault(); toast("Use at least 6 characters for the password.", true); return; }
      if (pw !== cf) { e.preventDefault(); toast("The two passwords do not match.", true); }
    });
  }
})();


/* ============================================================
   Product image picker - preserves the original page behaviour
   ============================================================ */
(function () {
  "use strict";

  var input = document.getElementById("productImages");
  var preview = document.getElementById("imagePreview");
  var badge = document.getElementById("imageCountBadge");
  var error = document.getElementById("imageError");
  var form = document.getElementById("sellForm");

  if (!input || !preview || !badge || !form) return;

  var dt = new DataTransfer();

  function showError(message) {
    if (!error) return;
    error.textContent = message;
    error.removeAttribute("hidden");
  }

  function clearError() {
    if (!error) return;
    error.textContent = "";
    error.setAttribute("hidden", "hidden");
  }

  function render() {
    preview.innerHTML = "";
    badge.textContent = dt.files.length + (dt.files.length === 1 ? " image" : " images");

    Array.prototype.forEach.call(dt.files, function (file, index) {
      var card = document.createElement("div");
      card.className = "image-preview-card";

      var img = document.createElement("img");
      img.alt = file.name;
      img.src = URL.createObjectURL(file);

      var info = document.createElement("div");
      info.className = "image-preview-info";
      info.textContent = file.name + " (" + (file.size / 1024 / 1024).toFixed(1) + " MB)";

      var remove = document.createElement("button");
      remove.type = "button";
      remove.className = "btn btn-ghost btn-sm image-preview-remove";
      remove.textContent = "Remove";
      remove.addEventListener("click", function () {
        dt.items.remove(index);
        input.files = dt.files;
        render();
        clearError();
      });

      card.appendChild(img);
      card.appendChild(info);
      card.appendChild(remove);
      preview.appendChild(card);
    });
  }

  input.addEventListener("change", function () {
    clearError();

    var selected = Array.prototype.slice.call(input.files);

    for (var i = 0; i < selected.length; i++) {
      var file = selected[i];

      if (dt.files.length >= 5) {
        showError("Maximum 5 images allowed.");
        break;
      }

      if (file.size > 5 * 1024 * 1024) {
        showError(file.name + " exceeds the 5 MB limit.");
        continue;
      }

      if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) {
        showError(file.name + " is not a supported image type.");
        continue;
      }

      var duplicate = false;
      for (var j = 0; j < dt.files.length; j++) {
        if (dt.files[j].name === file.name &&
            dt.files[j].size === file.size &&
            dt.files[j].lastModified === file.lastModified) {
          duplicate = true;
          break;
        }
      }

      if (!duplicate) dt.items.add(file);
    }

    input.files = dt.files;
    render();
  });

  form.addEventListener("submit", function (event) {
    clearError();

    if (dt.files.length < 2) {
      event.preventDefault();
      showError("Please select at least 2 product images.");
      return;
    }

    if (dt.files.length > 5) {
      event.preventDefault();
      showError("You can upload a maximum of 5 images.");
    }
  });
})();
/* ==========================================================================
   CIRCULAR CAMPUS - SAFE PRODUCT CARD 3D EFFECT
   ========================================================================== */

(function () {
    'use strict';

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        return;
    }

    const cards = document.querySelectorAll('.product-card');

    cards.forEach(function (card) {

        card.addEventListener('mousemove', function (event) {
            const rect = card.getBoundingClientRect();

            const x = event.clientX - rect.left;
            const y = event.clientY - rect.top;

            const centerX = rect.width / 2;
            const centerY = rect.height / 2;

            const rotateX = ((y - centerY) / centerY) * -3;
            const rotateY = ((x - centerX) / centerX) * 3;

            card.style.transform =
                'perspective(900px) rotateX(' +
                rotateX +
                'deg) rotateY(' +
                rotateY +
                'deg) translateY(-6px)';
        });

        card.addEventListener('mouseleave', function () {
            card.style.transform =
                'perspective(900px) rotateX(0deg) rotateY(0deg) translateY(0)';
        });
    });

  var amountChips = document.querySelectorAll(".amount-chip[data-amount]");
  amountChips.forEach(function (chip) {
    chip.addEventListener("click", function () {
      var input = document.querySelector('input[name="amount"]');
      if (input) input.value = chip.getAttribute("data-amount");
      amountChips.forEach(function (c) { c.classList.remove("is-selected"); });
      chip.classList.add("is-selected");
    });
  });

})();