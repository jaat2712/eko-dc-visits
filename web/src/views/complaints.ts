/**
 * COMPLAINTS: what CSPs told their DC at the shop, written in the DC app
 * (sync op csp.complaint, app 0.20.0+). Newest first, IST date range.
 *
 * Scoping is SERVER-SIDE (C6); this view renders exactly what the API returns.
 */
import { ApiError, listComplaints, type ComplaintCategory, type ComplaintRow } from '../api/client.ts';
import { formatIstDateTime, todayIstDate } from '../lib/format.ts';

export const CATEGORY_LABEL: Record<ComplaintCategory, string> = {
  DEVICE: 'Device / machine',
  PAYMENT: 'Payment / settlement',
  COMMISSION: 'Commission / charges',
  SUPPORT: 'Eko support',
  TRAINING: 'Training',
  OTHER: 'Other',
};

export function renderComplaintsView(root: HTMLElement): () => void {
  root.innerHTML = `
    <main class="day">
      <header class="topbar">
        <h1>Complaints</h1>
        <label>From (IST) <input id="cmp-from" type="date" /></label>
        <label>To <input id="cmp-to" type="date" /></label>
      </header>
      <div id="cmp-error" class="error-box" role="alert" hidden>
        <span id="cmp-error-msg"></span>
        <button id="cmp-retry" type="button">Retry</button>
      </div>
      <p id="cmp-summary" class="status"></p>
      <table class="visits">
        <thead>
          <tr><th>Date (IST)</th><th>DC</th><th>CSP</th><th>District</th><th>Type</th><th>Complaint</th></tr>
        </thead>
        <tbody id="cmp-body"></tbody>
      </table>
      <p id="cmp-empty" class="empty" hidden>No complaints in these dates</p>
    </main>
  `;

  const fromEl = root.querySelector<HTMLInputElement>('#cmp-from')!;
  const toEl = root.querySelector<HTMLInputElement>('#cmp-to')!;
  const errorBox = root.querySelector<HTMLElement>('#cmp-error')!;
  const errorMsg = root.querySelector<HTMLElement>('#cmp-error-msg')!;
  const summaryEl = root.querySelector<HTMLElement>('#cmp-summary')!;
  const tbody = root.querySelector<HTMLTableSectionElement>('#cmp-body')!;
  const emptyEl = root.querySelector<HTMLElement>('#cmp-empty')!;

  const today = todayIstDate();
  toEl.value = today;
  fromEl.value = todayIstDate(new Date(Date.now() - 29 * 86_400_000)); // last 30 days

  let inflight: AbortController | null = null;

  function render(rows: readonly ComplaintRow[]): void {
    errorBox.hidden = true;
    emptyEl.hidden = rows.length > 0;
    summaryEl.textContent = rows.length > 0 ? `${rows.length} complaint${rows.length === 1 ? '' : 's'}` : '';
    tbody.replaceChildren(
      ...rows.map((r) => {
        const tr = document.createElement('tr');
        tr.append(
          td(formatIstDateTime(r.created_at)),
          td(r.dc_name),
          td(r.csp_name ? `${r.csp_code} · ${r.csp_name}` : r.csp_code),
          td(r.district ?? '—'),
          td(CATEGORY_LABEL[r.category] ?? r.category),
          td(r.text),
        );
        return tr;
      }),
    );
  }

  async function load(): Promise<void> {
    inflight?.abort();
    const ctrl = new AbortController();
    inflight = ctrl;
    try {
      const res = await listComplaints(fromEl.value || today, toEl.value || today, ctrl.signal);
      if (ctrl.signal.aborted) return;
      render(res.items);
    } catch (err) {
      if (ctrl.signal.aborted) return;
      errorMsg.textContent = err instanceof ApiError ? err.message : 'Could not load complaints';
      errorBox.hidden = false;
    }
  }

  fromEl.addEventListener('change', () => void load());
  toEl.addEventListener('change', () => void load());
  root.querySelector<HTMLButtonElement>('#cmp-retry')!.addEventListener('click', () => void load());
  void load();

  return () => inflight?.abort();
}

function td(text: string): HTMLTableCellElement {
  const cell = document.createElement('td');
  cell.textContent = text;
  return cell;
}
