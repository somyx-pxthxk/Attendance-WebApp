const $ = (id) => document.getElementById(id);

let mode = "now"; // "now" | "plan"

function setMode(next) {
  mode = next;
  $("modeNow").classList.toggle("seg-btn--active", mode === "now");
  $("modePlan").classList.toggle("seg-btn--active", mode === "plan");
  $("upcomingWrap").classList.toggle("hidden", mode !== "plan");
  clearError();
  hideResult();
}

function valInt(id) {
  const raw = $(id).value.trim();
  if (raw === "") return null;
  const n = Number(raw);
  if (!Number.isFinite(n) || !Number.isInteger(n)) return NaN;
  return n;
}

function showError(msg) {
  const el = $("err");
  el.textContent = msg;
  el.classList.remove("hidden");
}

function clearError() {
  const el = $("err");
  el.textContent = "";
  el.classList.add("hidden");
}

function hideResult() {
  $("result").classList.add("hidden");
  $("resultBody").innerHTML = "";
}

function metric(label, valueHtml) {
  return `
    <div class="metric">
      <div class="k">${label}</div>
      <div class="v">${valueHtml}</div>
    </div>
  `;
}

function pctFmt(n) {
  return `${Number(n).toFixed(2)}%`;
}

async function calculate() {
  clearError();
  hideResult();

  const totalHeld = valInt("totalHeld");
  const attended = valInt("attended");
  const upcoming = valInt("upcoming");

  if (totalHeld === null || attended === null) {
    showError("Please fill Total held and Attended.");
    return;
  }
  if (Number.isNaN(totalHeld) || Number.isNaN(attended)) {
    showError("Please enter valid whole numbers.");
    return;
  }
  if (totalHeld < 0 || attended < 0) {
    showError("Values must be non-negative.");
    return;
  }
  if (attended > totalHeld) {
    showError("Attended cannot be greater than Total held.");
    return;
  }

  if (mode === "plan") {
    if (upcoming === null) {
      showError("Please fill Upcoming classes.");
      return;
    }
    if (Number.isNaN(upcoming) || upcoming < 0) {
      showError("Upcoming must be a non-negative whole number.");
      return;
    }
  }

  const params = new URLSearchParams();
  params.set("mode", mode);
  params.set("totalHeld", String(totalHeld));
  params.set("attended", String(attended));
  if (mode === "plan") params.set("upcoming", String(upcoming));

  const res = await fetch(`/api/calc?${params.toString()}`);
  const data = await res.json();
  if (!res.ok) {
    showError(data?.error || "Something went wrong.");
    return;
  }

  const body = $("resultBody");

  if (data.mode === "now") {
    const badge = data.ok
      ? `<span class="badge ok">OK (≥ 75%)</span>`
      : `<span class="badge low">LOW (&lt; 75%)</span>`;
    body.innerHTML =
      metric("Current attendance", `${pctFmt(data.pct)} <span style="font-size:12px;color:#b8c0d9">(${data.attended}/${data.totalHeld})</span>`) +
      metric("Status", badge) +
      metric("Leaves you can take now", String(data.leavesNow)) +
      metric("Classes needed to reach 75%", String(data.classesNeededToReach));
  } else {
    body.innerHTML =
      metric("Upcoming classes", String(data.upcoming)) +
      metric("Min classes to attend (upcoming)", String(data.minAttendUpcoming)) +
      metric("Max leaves you can take (upcoming)", String(data.maxLeavesUpcoming)) +
      metric("Final attendance (if you attend minimum)", pctFmt(data.finalPctMin));
  }

  $("result").classList.remove("hidden");
}

function resetAll() {
  $("totalHeld").value = "";
  $("attended").value = "";
  $("upcoming").value = "";
  clearError();
  hideResult();
  setMode("now");
}

window.addEventListener("DOMContentLoaded", () => {
  $("modeNow").addEventListener("click", () => setMode("now"));
  $("modePlan").addEventListener("click", () => setMode("plan"));
  $("calcBtn").addEventListener("click", () => calculate().catch((e) => showError(String(e))));
  $("resetBtn").addEventListener("click", resetAll);
  setMode("now");
});

