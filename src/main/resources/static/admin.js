'use strict';

const summaryBox = document.getElementById('summary');
const listBox = document.getElementById('list');
const refreshButton = document.getElementById('refresh');

const STEP_LABELS = {
  agree: 'Согласие',
  place: 'Место',
  date: 'Дата',
  time: 'Время',
};

const STEP_ORDER = ['agree', 'place', 'date', 'time'];

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

function formatWhen(value) {
  if (!value) {
    return '';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function formatDate(value) {
  const [year, month, day] = value.split('-').map(Number);
  return new Intl.DateTimeFormat('ru-RU', { weekday: 'short', day: 'numeric', month: 'long' }).format(
    new Date(year, month - 1, day),
  );
}

function answerValue(answer) {
  if (answer.step === 'agree') {
    return 'Да';
  }
  if (answer.step === 'place') {
    return answer.value === 'custom' ? `Свой вариант: ${answer.note}` : answer.value;
  }
  if (answer.step === 'date') {
    return formatDate(answer.value);
  }
  return answer.value;
}

function renderInvitation(invitation) {
  const card = el('div', `card-invitation${invitation.completedAt ? ' is-completed' : ''}`);
  const head = el('div', 'card-head');
  const badge = el('span', `badge${invitation.completedAt ? ' ok' : ''}`,
    invitation.completedAt ? 'все ответы' : `шаг: ${STEP_LABELS[invitation.currentStep] || '—'}`);
  head.append(badge, el('span', 'muted', `зашла ${formatWhen(invitation.createdAt)}`));
  card.append(head);

  const answers = new Map(invitation.answers.map((answer) => [answer.step, answer]));
  STEP_ORDER.forEach((step) => {
    const answer = answers.get(step);
    if (!answer) {
      return;
    }
    const row = el('div', 'answer-row');
    row.append(el('span', null, STEP_LABELS[step]), el('span', null, answerValue(answer)));
    card.append(row);
  });
  return card;
}

async function load() {
  refreshButton.disabled = true;
  try {
    const response = await fetch('/api/admin/invitations', { headers: { Accept: 'application/json' } });
    if (!response.ok) {
      throw new Error('Не удалось загрузить ответы');
    }
    const data = await response.json();
    summaryBox.replaceChildren(
      stat(String(data.summary.total), 'всего заходов'),
      stat(String(data.summary.completed), 'ответили полностью'),
    );
    if (!data.invitations.length) {
      listBox.replaceChildren(el('p', 'muted', 'Пока никто не открывал приглашение'));
      return;
    }
    const fragment = document.createDocumentFragment();
    data.invitations.forEach((invitation) => fragment.append(renderInvitation(invitation)));
    listBox.replaceChildren(fragment);
  } catch (error) {
    listBox.replaceChildren(el('p', 'muted', error.message));
  } finally {
    refreshButton.disabled = false;
  }
}

function stat(value, label) {
  const box = el('div', 'stat');
  box.append(el('b', null, value), el('span', null, label));
  return box;
}

refreshButton.addEventListener('click', load);
load();
