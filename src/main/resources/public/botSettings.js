fetch('/wywoz-initial-status')
    .then(res => res.text())
    .then(status => {
        if (status === 'disabled') {
            document.getElementById('wywozForm').style.display = 'none';
            document.getElementById('bindingsTable').style.display = 'none';
            document.getElementById('addBindingForm').style.display = 'none';
            document.getElementById('newBindingTxt').innerHTML = 'Auto-Kick is disabled';
        }
    });

fetch('/wywoz-status')
    .then(res => res.text())
    .then(status => {
        document.getElementById('wywozSmieciToggle').checked = (status === 'enabled');
        if (status === 'disabled') {
            document.getElementById('bindingsTable').style.display = 'none';
            document.getElementById('addBindingForm').style.display = 'none';
            document.getElementById('newBindingTxt').innerHTML = 'Auto-Kick is disabled';
        } else {
            document.getElementById('bindingsTable').style.display = 'table';
            document.getElementById('addBindingForm').style.display = 'auto';
            document.getElementById('newBindingTxt').innerHTML = 'New Auto-Kick';
        }
    });

fetch('/temp-role-initial-status')
    .then(res => res.text())
    .then(status => {
        if (status === 'disabled') {
            document.getElementById('tempRoleForm').style.display = 'none';
            document.getElementById('rolesTable').style.display = 'none';
            document.getElementById('addRoleForm').style.display = 'none';
            document.getElementById('newRoleTxt').innerHTML = 'Temp Role is disabled';
        }
    });
fetch('/temp-role-status')
    .then(res => res.text())
    .then(status => {
        document.getElementById('tempRoleToggle').checked = (status === 'enabled');
        if (status === 'disabled') {
            document.getElementById('rolesTable').style.display = 'none';
            document.getElementById('addRoleForm').style.display = 'none';
            document.getElementById('newRoleTxt').innerHTML = 'Temp Role is disabled';
        } else {
            document.getElementById('rolesTable').style.display = 'table';
            document.getElementById('addRoleForm').style.display = 'auto';
            document.getElementById('newRoleTxt').innerHTML = 'New Temporary Role';
        }
    });

fetch('/debug-status')
    .then(res => res.text())
    .then(status => {
        document.getElementById('debugToggle').checked = (status === 'enabled');
    });

function loadBindings() {
    fetch('/bindings-detailed')
        .then(res => res.json())
        .then(bindings => {
            const tbody = document.getElementById('bindingsTable').querySelector('tbody');
            tbody.innerHTML = '';
            bindings.forEach(b => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                                <td>${b.userName}<br>(${b.userId})</td>
                                <td>${b.channelName}<br>(${b.channelId})</td>
                                <td>
                                    <input type="checkbox" ${b.enabled ? 'checked' : ''} onchange="toggleBinding('${b.userId}', '${b.channelId}', this.checked)">
                                </td>
                                <td>
                                    <button onclick="removeBinding('${b.userId}', '${b.channelId}')">Remove</button>
                                </td>
                            `;
                tbody.appendChild(tr);
            });
        });
}

function toggleBinding(userId, channelId, enabled) {
    console.log('Toggling:', userId, channelId, enabled);
    fetch('/toggle-binding', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `userId=${userId}&channelId=${channelId}&enabled=${enabled}`
    }).then(loadBindings);
}

function removeBinding(userId, channelId) {
    fetch('/remove-binding', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `userId=${userId}&channelId=${channelId}`
    }).then(loadBindings);
}

document.getElementById('addBindingForm').onsubmit = function(e) {
    e.preventDefault();
    const form = e.target;
    const userId = form.userId.value;
    const channelId = form.channelId.value;
    fetch('/add-binding', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `userId=${userId}&channelId=${channelId}`
    }).then(() => {
        form.reset();
        loadBindings();
    });
};
loadBindings();

function loadTempRoles() {
    fetch('/temp-roles-detailed')
        .then(res => res.json())
        .then(bindings => {
            const tbody = document.getElementById('rolesTable').querySelector('tbody');
            tbody.innerHTML = '';
            bindings.forEach(b => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                                <td>${b.roleName}<br>(${b.roleId})</td>
                                <td>${b.channelName}<br>(${b.channelId})</td>
                                <td>
                                    <input type="checkbox" ${b.enabled ? 'checked' : ''} onchange="toggleTempRole('${b.roleId}', '${b.channelId}', this.checked)">
                                </td>
                                <td>
                                    <button onclick="removeTempRole('${b.roleId}', '${b.channelId}')">Remove</button>
                                </td>
                            `;
                tbody.appendChild(tr);
            });
        });
}
loadTempRoles();

function toggleTempRole(roleId, channelId, enabled) {
    console.log('Toggling:', roleId, channelId, enabled);
    fetch('/toggle-temp-role', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `roleId=${roleId}&channelId=${channelId}&enabled=${enabled}`
    }).then(loadTempRoles);
}

document.getElementById('addRoleForm').onsubmit = function(e) {
    e.preventDefault();
    const form = e.target;
    const roleId = form.roleId.value;
    const channelId = form.channelId.value;
    fetch('/add-temp-role', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `roleId=${roleId}&channelId=${channelId}`
    }).then(() => {
        form.reset();
        loadTempRoles();
    });
};

function removeTempRole(roleId, channelId) {
    fetch('/remove-temp-role', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `roleId=${roleId}&channelId=${channelId}`
    }).then(loadTempRoles);
}

fetch('/get-info')
    .then(res => res.json())
    .then(data => {
        document.getElementById('fileName').innerHTML= data.fullVersion;
        document.getElementById('lastRestart').innerHTML = data.lastRestart;
    })
    .catch(err => {
        document.getElementById('fileName').innerHTML= "Error loading info - " + err.message;
        document.getElementById('lastRestart').innerHTML = "Error loading info - " + err.message;
    });