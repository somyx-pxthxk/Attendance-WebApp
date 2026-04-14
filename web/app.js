const $ = (id) => document.getElementById(id);

let mode = "now"; // "now" | "plan"

function meetsMinimum(attended, totalHeld) {
  return 4 * attended >= 3 * totalHeld;
}

function classesNeededToReachMinimum(totalHeld, attended) {
  return Math.max(0, 3 * totalHeld - 4 * attended);
}

function leavesYouCanTakeNow(totalHeld, attended) {
  const numerator = 4 * attended - 3 * totalHeld;
  if (numerator <= 0) return 0;
  return Math.floor(numerator / 3);
}

function ceilDiv(a, b) {
  if (a <= 0) return 0;
  return Math.floor((a + b - 1) / b);
}

function minUpcomingAttendanceNeeded(totalHeld, attended, upcoming) {
  const needed = ceilDiv(3 * (totalHeld + upcoming) - 4 * attended, 4);
  return Math.max(0, Math.min(upcoming, needed));
}

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

function calculate() {
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

  const body = $("resultBody");

  if (mode === "now") {
    const pct = totalHeld === 0 ? 0 : (attended * 100) / totalHeld;
    const ok = meetsMinimum(attended, totalHeld);
    const leavesNow = leavesYouCanTakeNow(totalHeld, attended);
    const need = classesNeededToReachMinimum(totalHeld, attended);
    const badge = ok
      ? `<span class="badge ok">OK (≥ 75%)</span>`
      : `<span class="badge low">LOW (&lt; 75%)</span>`;
    body.innerHTML =
      metric("Current attendance", `${pctFmt(pct)} <span style="font-size:12px;color:#b8c0d9">(${attended}/${totalHeld})</span>`) +
      metric("Status", badge) +
      metric("Leaves you can take now", String(leavesNow)) +
      metric("Classes needed to reach 75%", String(need));
  } else {
    const minAttendUpcoming = minUpcomingAttendanceNeeded(totalHeld, attended, upcoming);
    const maxLeavesUpcoming = upcoming - minAttendUpcoming;
    const finalTotal = totalHeld + upcoming;
    const finalAttendMin = attended + minAttendUpcoming;
    const finalPctMin = finalTotal === 0 ? 0 : (finalAttendMin * 100) / finalTotal;
    body.innerHTML =
      metric("Upcoming classes", String(upcoming)) +
      metric("Min classes to attend (upcoming)", String(minAttendUpcoming)) +
      metric("Max leaves you can take (upcoming)", String(maxLeavesUpcoming)) +
      metric("Final attendance (if you attend minimum)", pctFmt(finalPctMin));
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
  $("calcBtn").addEventListener("click", () => calculate());
  $("resetBtn").addEventListener("click", resetAll);
  setMode("now");
});

