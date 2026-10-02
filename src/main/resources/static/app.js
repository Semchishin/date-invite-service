'use strict';

const STEPS = ['agree', 'place', 'date', 'time'];
const LAST_STEP = STEPS[STEPS.length - 1];
const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
const CUSTOM_PLACE = 'custom';
const TOKEN_KEY = 'invitationToken';

const app = document.getElementById('app');

const state = {
  content: null,
  invitation: null,
  invitationId: null,
  draft: { agree: null, place: null, date: null, time: null },
  step: 'agree',
  view: null,
  error: null,
  busy: false,
  confirmed: false,
  noGone: false,
  customOpen: false,
  month: null,
};

function rememberToken(id) {
  state.invitationId = id;
  try {
    window.localStorage.setItem(TOKEN_KEY, id);
  } catch (error) {
  }
}

function knownToken() {
  return state.invitationId || null;
}

function storedToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch (error) {
    return null;
  }
}

function forgetToken() {
  state.invitationId = null;
  try {
    window.localStorage.removeItem(TOKEN_KEY);
  } catch (error) {
  }
}

async function api(path, options = {}) {
  const token = knownToken();
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json', ...(options.headers || {}) };
  if (token) {
    headers['X-Invitation-Token'] = token;
  }
  const response = await fetch(path, { ...options, headers, credentials: 'omit' });
  if (response.status === 204) {
    return null;
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error((body && body.error) || 'Что-то пошло не так, попробуй ещё раз');
  }
  return body;
}

async function init() {
  try {
    const content = await api('/api/content');
    state.content = content;
    state.invitation = await resolveInvitation();
    state.confirmed = Boolean(state.invitation.completedAt);
    state.month = firstMonthWithDay();
    render();
  } catch (error) {
    renderFatal(error.message);
  }
}

async function resolveInvitation() {
  const stored = storedToken();
  if (stored) {
    state.invitationId = stored;
    try {
      return await api('/api/invitations/current');
    } catch (error) {
      forgetToken();
    }
  }
  const created = await api('/api/invitations', { method: 'POST' });
  rememberToken(created.id);
  return created;
}

function choose(step, value, note) {
  state.draft[step] = { value, note: note || null };
  const wasEditing = Boolean(state.view);
  state.view = null;
  state.customOpen = false;
  state.error = null;
  state.step = wasEditing || step === LAST_STEP ? null : STEPS[STEPS.indexOf(step) + 1];
  render();
}

async function confirm() {
  if (state.busy) {
    return;
  }
  state.busy = true;
  state.error = null;
  render();
  try {
    const answers = STEPS.filter((step) => state.draft[step]).map((step) => ({
      step,
      value: state.draft[step].value,
      note: state.draft[step].note,
    }));
    state.invitation = await api('/api/invitations/confirm', {
      method: 'POST',
      body: JSON.stringify({ answers }),
    });
    state.confirmed = true;
  } catch (error) {
    state.error = error.message;
  } finally {
    state.busy = false;
    render();
  }
}

function render() {
  if (state.confirmed) {
    app.replaceChildren(renderWaiting());
    return;
  }
  const step = state.view || state.step;
  const fragment = document.createDocumentFragment();
  fragment.append(renderDots(step));
  if (state.error) {
    fragment.append(renderAlert(state.error));
  }
  if (step) {
    const texts = textsFor(step);
    fragment.append(renderHeader(texts.title, texts.subtitle, step));
    fragment.append(renderChosen());
    fragment.append({
      agree: renderAgree,
      place: renderPlace,
      date: renderDate,
      time: renderTime,
    }[step]());
  } else {
    fragment.append(renderFinale());
  }
  app.replaceChildren(fragment);
}

function renderFatal(message) {
  app.replaceChildren(renderAlert(message));
}

function renderDots(step) {
  const dots = el('div', 'dots');
  const currentIndex = step ? STEPS.indexOf(step) : STEPS.length;
  STEPS.forEach((key, index) => {
    const done = index < currentIndex;
    const active = index === currentIndex;
    dots.append(el('div', `dot${done ? ' is-done' : ''}${active ? ' is-active' : ''}`));
  });
  return dots;
}

function renderAlert(message) {
  const alert = el('div', 'alert');
  alert.setAttribute('role', 'alert');
  alert.append(el('span', null, '⚠️'), el('span', null, message));
  return alert;
}

function renderHeader(title, subtitle, step) {
  const wrap = el('div', 'step');
  wrap.append(el('h1', 'title', title));
  if (subtitle) {
    wrap.append(el('p', step ? `subtitle subtitle-${step}` : 'subtitle', subtitle));
  }
  if (state.view) {
    const back = el('button', 'btn-link', textsBack());
    back.type = 'button';
    back.addEventListener('click', () => {
      state.view = null;
      state.customOpen = false;
      state.error = null;
      render();
    });
    wrap.append(back);
  }
  return wrap;
}

function renderAgree() {
  const wrap = el('div', 'step');
  const actions = el('div', 'actions');
  const yes = el('button', 'btn', textsFor('agree').yes || 'Да');
  yes.type = 'button';
  if (state.draft.agree) {
    yes.append(el('span', null, ' ✓'));
  }
  yes.addEventListener('click', () => choose('agree', 'yes'));
  actions.append(yes);

  if (!state.noGone) {
    const no = el('button', 'btn btn-ghost', textsFor('agree').no || 'Нет');
    no.type = 'button';
    no.addEventListener('click', () => {
      state.noGone = true;
      state.error = state.content.texts.errorWrongAnswer;
      render();
    });
    actions.append(no);
  }
  wrap.append(actions);
  return wrap;
}

function renderPlace() {
  const wrap = el('div', 'step');
  const grid = el('div', 'places');
  const answer = state.draft.place;
  state.content.places.forEach((place) => {
    const card = el('button', 'place');
    card.type = 'button';
    if (answer && answer.value === place.id) {
      card.append(el('span', 'place-check', '✓'));
    }
    const image = el('img');
    image.src = place.image;
    image.alt = place.title;
    image.loading = 'lazy';
    card.append(image);
    const body = el('div', 'place-body');
    body.append(el('div', 'place-title', place.title));
    if (place.description) {
      body.append(el('div', 'place-desc', place.description));
    }
    card.append(body);
    card.addEventListener('click', () => choose('place', place.id));
    grid.append(card);
  });
  wrap.append(grid);

  const customAnswer = answer && answer.value === CUSTOM_PLACE ? answer : null;
  if (state.customOpen) {
    grid.append(renderCustomPlace(customAnswer));
  } else {
    const custom = state.content.texts.customPlace || { label: 'Свой вариант', placeholder: '' };
    const customCard = el('button', 'place place-wide');
    customCard.type = 'button';
    if (customAnswer) {
      customCard.append(el('span', 'place-check', '✓'));
    }
    const customBody = el('div', 'place-body');
    customBody.append(el('div', 'place-title', custom.label));
    customBody.append(el('div', 'place-desc', customAnswer ? customAnswer.note : 'Напиши своё'));
    customCard.append(customBody);
    customCard.addEventListener('click', () => {
      state.customOpen = true;
      render();
      const input = document.getElementById('custom-place-input');
      if (input) {
        input.focus();
      }
    });
    grid.append(customCard);
  }
  return wrap;
}

function renderCustomPlace(customAnswer) {
  const box = el('div', 'custom');
  const row = el('div', 'custom-row');
  const input = el('input');
  input.type = 'text';
  input.id = 'custom-place-input';
  input.maxLength = 200;
  input.placeholder = (state.content.texts.customPlace || {}).placeholder || '';
  input.value = customAnswer ? customAnswer.note || '' : '';
  const confirmButton = el('button', 'btn', 'OK');
  confirmButton.type = 'button';
  const send = () => {
    const value = input.value.trim();
    if (!value) {
      input.focus();
      return;
    }
    choose('place', CUSTOM_PLACE, value);
  };
  confirmButton.addEventListener('click', send);
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      send();
    }
  });
  row.append(input, confirmButton);
  box.append(row);
  return box;
}

function renderDate() {
  const wrap = el('div', 'step');
  wrap.append(renderCalendar());
  return wrap;
}

function renderCalendar() {
  const box = el('div', 'calendar');
  const head = el('div', 'calendar-head');
  const previous = el('button', 'nav', '‹');
  previous.type = 'button';
  previous.setAttribute('aria-label', 'Предыдущий месяц');
  previous.disabled = state.month.getTime() <= firstMonthWithDay().getTime();
  previous.addEventListener('click', () => {
    state.month = new Date(state.month.getFullYear(), state.month.getMonth() - 1, 1);
    render();
  });
  const next = el('button', 'nav', '›');
  next.type = 'button';
  next.setAttribute('aria-label', 'Следующий месяц');
  next.disabled = state.month.getTime() >= firstOfMonth(maxDate()).getTime();
  next.addEventListener('click', () => {
    state.month = new Date(state.month.getFullYear(), state.month.getMonth() + 1, 1);
    render();
  });
  head.append(previous, el('div', 'calendar-title', monthTitle(state.month)), next);

  const weekdays = el('div', 'weekdays');
  WEEKDAYS.forEach((day) => weekdays.append(el('span', null, day)));

  const days = el('div', 'days');
  const selected = state.draft.date ? state.draft.date.value : null;
  monthCells().forEach((date) => {
    if (!date) {
      days.append(el('div', 'day'));
      return;
    }
    const value = isoDate(date);
    const cell = el('button', 'day', String(date.getDate()));
    cell.type = 'button';
    cell.disabled = isDayDisabled(date);
    if (value === selected) {
      cell.classList.add('is-selected');
    }
    cell.addEventListener('click', () => choose('date', value));
    days.append(cell);
  });

  box.append(head, weekdays, days);
  return box;
}

function renderTime() {
  const wrap = el('div', 'step');
  const grid = el('div', 'times');
  const selected = state.draft.time ? state.draft.time.value : null;
  (state.content.schedule.times || []).forEach((time) => {
    const button = el('button', 'time', time);
    button.type = 'button';
    if (time === selected) {
      button.classList.add('is-selected');
    }
    button.addEventListener('click', () => choose('time', time));
    grid.append(button);
  });
  wrap.append(grid);
  if (!(state.content.schedule.times || []).length) {
    wrap.append(el('p', 'subtitle', 'Время пока не настроено'));
  }
  return wrap;
}

function renderFinale() {
  const wrap = el('div', 'step');
  const texts = state.content.texts.finale || {};
  wrap.append(el('h1', 'title', texts.title || 'Всё готово'));
  if (texts.message) {
    wrap.append(el('p', 'subtitle', texts.message));
  }
  wrap.append(renderSummary());

  const actions = el('div', 'actions');
  const confirmButton = el('button', 'btn', state.content.texts.confirm || 'Подтвердить');
  confirmButton.type = 'button';
  confirmButton.disabled = state.busy;
  confirmButton.addEventListener('click', confirm);
  actions.append(confirmButton);
  wrap.append(actions);
  return wrap;
}

function renderWaiting() {
  const wrap = el('div', 'step waiting');
  wrap.append(el('div', 'heart', '❤️'));
  wrap.append(el('h1', 'title waiting-title', state.content.texts.waiting || 'Жду встречи'));
  const summary = renderSummary({ fromInvitation: true, editable: false });
  if (summary.childElementCount) {
    wrap.append(summary);
  }
  const sign = state.content.texts.sign || {};
  if (sign.thanks || sign.author) {
    const block = el('div', 'sign');
    if (sign.thanks) {
      block.append(el('p', 'sign-thanks', sign.thanks));
    }
    if (sign.author) {
      block.append(el('p', 'sign-author', sign.author));
    }
    wrap.append(block);
  }
  return wrap;
}

function renderSummary({ fromInvitation = false, editable = true } = {}) {
  const texts = state.content.texts.finale || {};
  const summary = el('div', 'summary');
  const answers = fromInvitation ? savedAnswers() : state.draft;
  if (answers.place) {
    summary.append(summaryRow(texts.placeLabel || 'Место', placeTitle(answers.place), 'place', editable));
  }
  if (answers.date) {
    summary.append(summaryRow(texts.dateLabel || 'Дата', formatDate(answers.date.value), 'date', editable));
  }
  if (answers.time) {
    summary.append(summaryRow(texts.timeLabel || 'Время', answers.time.value, 'time', editable));
  }
  return summary;
}

function savedAnswers() {
  const saved = {};
  (state.invitation.answers || []).forEach((answer) => {
    saved[answer.step] = { value: answer.value, note: answer.note };
  });
  return saved;
}

function summaryRow(label, value, step, editable) {
  const row = el('div', 'summary-row');
  const text = el('div');
  text.append(el('div', 'summary-label', label), el('div', 'summary-value', value));
  row.append(text);
  if (editable) {
    const edit = el('button', 'summary-edit', textsEdit());
    edit.type = 'button';
    edit.disabled = state.busy;
    edit.addEventListener('click', () => {
      state.view = step;
      state.customOpen = false;
      state.error = null;
      render();
    });
    row.append(edit);
  }
  return row;
}

function renderChosen() {
  const chosen = el('div', 'chosen');
  const place = state.draft.place;
  const date = state.draft.date;
  const time = state.draft.time;
  const entries = [
    place ? { step: 'place', label: placeTitle(place) } : null,
    date ? { step: 'date', label: formatDate(date.value) } : null,
    time ? { step: 'time', label: time.value } : null,
  ].filter(Boolean);
  entries.forEach((entry) => {
    const chip = el('button', 'chip', entry.label);
    chip.type = 'button';
    chip.addEventListener('click', () => {
      state.view = entry.step;
      state.customOpen = false;
      state.error = null;
      render();
    });
    chosen.append(chip);
  });
  return chosen;
}

function placeTitle(answer) {
  if (answer.value === CUSTOM_PLACE) {
    return answer.note;
  }
  const place = (state.content.places || []).find((item) => item.id === answer.value);
  return place ? place.title : answer.value;
}

function textsFor(step) {
  const steps = state.content.texts.steps || {};
  return steps[step] || { title: '', subtitle: '' };
}

function textsBack() {
  return state.content.texts.back || 'Назад';
}

function textsEdit() {
  return state.content.texts.edit || 'Изменить';
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = text;
  }
  return node;
}

function today() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function firstOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function maxDate() {
  const limit = today();
  const maxAdvanceDays = (state.content.schedule || {}).maxAdvanceDays;
  limit.setDate(limit.getDate() + (typeof maxAdvanceDays === 'number' ? maxAdvanceDays : 90));
  return limit;
}

function isDayDisabled(date) {
  const schedule = state.content.schedule || {};
  if (date <= today() || date > maxDate()) {
    return true;
  }
  if ((schedule.excludedDates || []).includes(isoDate(date))) {
    return true;
  }
  return (schedule.excludedWeekdays || []).includes(date.getDay());
}

function firstMonthWithDay() {
  let month = firstOfMonth(today());
  for (let i = 0; i < 24 && !hasFreeDay(month); i += 1) {
    month = new Date(month.getFullYear(), month.getMonth() + 1, 1);
  }
  return month;
}

function hasFreeDay(month) {
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  for (let day = 1; day <= daysInMonth; day += 1) {
    if (!isDayDisabled(new Date(month.getFullYear(), month.getMonth(), day))) {
      return true;
    }
  }
  return false;
}

function monthCells() {
  const cells = [];
  const first = state.month;
  const offset = (first.getDay() + 6) % 7;
  for (let i = 0; i < offset; i += 1) {
    cells.push(null);
  }
  const start = today();
  const daysInMonth = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  for (let day = 1; day <= daysInMonth; day += 1) {
    const date = new Date(first.getFullYear(), first.getMonth(), day);
    cells.push(date.getTime() === start.getTime() ? null : date);
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  return cells;
}

function isoDate(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function monthTitle(date) {
  return new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' }).format(date);
}

function formatDate(value) {
  const [year, month, day] = value.split('-').map(Number);
  return new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' }).format(
    new Date(year, month - 1, day),
  );
}

init();
