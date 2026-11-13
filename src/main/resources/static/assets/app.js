'use strict';

const $ = (id) => document.getElementById(id);
const resources = { admins: 'Admins', teachers: 'Teachers', students: 'Students' };
let currentUser = null;
let editingId = null;
let busy = false;

function message(text = '', error = false) {
  $('message').textContent = text;
  $('message').classList.toggle('error', error);
}

// Send the session cookie and a CSRF token for every write request.
async function request(url, method = 'GET', body) {
  const headers = { Accept: 'application/json' };
  if (method !== 'GET') {
    const csrf = await request('/api/auth/csrf');
    headers['X-CSRF-TOKEN'] = csrf.token;
  }
  if (body) headers['Content-Type'] = 'application/json';
  const response = await fetch(url, {
    method, headers, credentials: 'same-origin',
    body: body ? JSON.stringify(body) : undefined
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && currentUser) showLogin();
    const error = new Error(data?.error || `Request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
}

// Prevent duplicate submissions while an operation is running.
async function run(action) {
  if (busy) return;
  busy = true;
  message();
  document.querySelectorAll('button, input, select').forEach((element) => element.disabled = true);
  try { await action(); }
  catch (error) { message(error instanceof TypeError ? 'Cannot connect to the server. Please try again.' : error.message, true); }
  finally {
    busy = false;
    document.querySelectorAll('button, input, select').forEach((element) => element.disabled = false);
  }
}

function resetForm() {
  editingId = null;
  $('account-form').reset();
  $('password').required = true;
  const kind = resources[$('resource').value]?.slice(0, -1) || 'account';
  const article = kind === 'Admin' ? 'an' : 'a';
  $('form-title').textContent = `Add ${kind.toLowerCase()}`;
  $('form-subtitle').textContent = `Create ${article} ${kind.toLowerCase()} account.`;
  $('save-button').textContent = `Add ${kind.toLowerCase()}`;
  $('password-hint').textContent = 'Use at least 8 characters.';
  $('cancel-button').hidden = true;
}

function showLogin() {
  currentUser = null;
  $('accounts').replaceChildren();
  resetForm();
  $('login-password').value = '';
  $('login-section').hidden = false;
  $('management-section').hidden = true;
  window.scrollTo(0, 0);
}

async function showManagement(user) {
  currentUser = user;
  $('login-section').hidden = true;
  $('management-section').hidden = false;
  window.scrollTo(0, 0);
  $('login-password').value = '';
  $('signed-in').textContent = `Logged in: ${user.name} (${user.role})`;
  $('page-title').textContent = user.role === 'ADMIN' ? 'Account management' : 'My profile';
  $('accounts-card').hidden = user.role !== 'ADMIN';
  $('workspace-grid').classList.toggle('profile-mode', user.role !== 'ADMIN');
  const allowed = user.role === 'ADMIN' ? Object.keys(resources) : [user.role.toLowerCase() + 's'];
  $('resource').replaceChildren(...allowed.map((key) => new Option(resources[key], key)));
  $('resource-controls').hidden = user.role !== 'ADMIN';
  await loadAccounts();
}

async function loadAccounts() {
  resetForm();
  $('list-title').textContent = resources[$('resource').value];
  $('list-subtitle').textContent = currentUser.role === 'ADMIN'
    ? 'View, edit, or delete an account.' : 'Your account details.';
  $('accounts').replaceChildren();
  const accounts = await request('/api/' + $('resource').value);
  if (!accounts.length) {
    const cell = $('accounts').insertRow().insertCell();
    cell.colSpan = 4;
    cell.textContent = 'No accounts found.';
  }
  for (const account of accounts) {
    const row = $('accounts').insertRow();
    for (const value of [account.id, account.name, account.email]) row.insertCell().textContent = value;
    const actions = row.insertCell();
    const actionsForAccount = currentUser.role === 'ADMIN' && account.id !== currentUser.id
      ? ['Edit', 'Delete'] : ['Edit'];
    for (const action of actionsForAccount) {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = action;
      button.setAttribute('aria-label', `${action} ${account.name}`);
      button.addEventListener('click', () => action === 'Edit' ? editAccount(account) : deleteAccount(account));
      actions.append(button);
    }
  }
  $('form-card').hidden = currentUser.role !== 'ADMIN' && accounts.length === 0;
  if (currentUser.role !== 'ADMIN' && accounts.length) editAccount(accounts[0], true);
}

function editAccount(account, fromLoad = false) {
  if (busy && !fromLoad) return;
  editingId = account.id;
  $('name').value = account.name;
  $('email').value = account.email;
  $('password').value = '';
  $('password').required = false;
  $('form-title').textContent = currentUser.role === 'ADMIN' ? 'Edit account' : 'Edit my account';
  $('form-subtitle').textContent = `Update ${account.name}'s details.`;
  $('password-hint').textContent = 'Leave blank to keep the current password.';
  $('save-button').textContent = 'Save changes';
  $('cancel-button').hidden = currentUser.role !== 'ADMIN';
  if (!fromLoad) $('name').focus();
}

async function logout() {
  await request('/api/auth/logout', 'POST');
  showLogin();
}

function deleteAccount(account) {
  if (account.id === currentUser.id) {
    message('You cannot delete your own account.', true);
    return;
  }
  if (busy || !confirm(`Delete ${account.name}?`)) return;
  run(async () => {
    await request(`/api/${$('resource').value}/${account.id}`, 'DELETE');
    await loadAccounts();
    message('Account deleted.');
  });
}

$('login-form').addEventListener('submit', (event) => {
  event.preventDefault();
  run(async () => {
    await request('/api/auth/login', 'POST', {
      email: $('login-email').value.trim(), password: $('login-password').value
    });
    await showManagement(await request('/api/auth/me'));
  });
});

$('account-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const body = { name: $('name').value.trim(), email: $('email').value.trim() };
  if (!body.name) { message('Name is required.', true); return; }
  if ($('password').value) body.password = $('password').value;
  run(async () => {
    const id = editingId;
    await request('/api/' + $('resource').value + (id === null ? '' : '/' + id), id === null ? 'POST' : 'PUT', body);
    if (id === currentUser.id && (body.email.toLowerCase() !== currentUser.email.toLowerCase() || body.password)) {
      await logout();
      message('Account updated. Please login with your new details.');
    } else {
      if (id === currentUser.id) {
        currentUser.name = body.name;
        $('signed-in').textContent = `Logged in: ${body.name} (${currentUser.role})`;
      }
      await loadAccounts();
      message(id === null ? 'Account created.' : 'Account updated.');
    }
  });
});

$('resource').addEventListener('change', () => run(loadAccounts));
$('cancel-button').addEventListener('click', resetForm);
$('logout-button').addEventListener('click', () => run(logout));

// Restore an existing login after a page refresh.
run(async () => {
  try { await showManagement(await request('/api/auth/me')); }
  catch (error) { if (error.status === 401) showLogin(); else throw error; }
});
