const dashboardSessionToken = window.dashboardSessionToken || localStorage.getItem('discordSessionToken');
const authParam = dashboardSessionToken ? `?sessionToken=${encodeURIComponent(dashboardSessionToken)}` : '';

console.log('botSettings.js initialized');
console.log('Session token available:', !!dashboardSessionToken);
console.log('Auth param:', authParam);

fetch('/wywoz-initial-status' + authParam)
    .then(res => {
        if (!res.ok) {
            console.error('Failed to fetch wywoz-initial-status:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('wywoz-initial-status:', status);
        if (status === 'disabled') {
            document.getElementById('wywozForm').style.display = 'none';
            document.getElementById('bindingsTable').style.display = 'none';
            document.getElementById('addBindingForm').style.display = 'none';
            document.getElementById('newBindingTxt').innerHTML = 'Auto-Kick is disabled';
        }
    })
    .catch(err => {
        console.error('Error fetching wywoz-initial-status:', err);
    });

fetch('/wywoz-status' + authParam)
    .then(res => {
        if (!res.ok) {
            console.error('Failed to fetch wywoz-status:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('wywoz-status:', status);
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
    })
    .catch(err => {
        console.error('Error fetching wywoz-status:', err);
    });

fetch('/temp-role-initial-status' + authParam)
    .then(res => {
        if (!res.ok) {
            console.error('Failed to fetch temp-role-initial-status:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('temp-role-initial-status:', status);
        if (status === 'disabled') {
            document.getElementById('tempRoleForm').style.display = 'none';
            document.getElementById('rolesTable').style.display = 'none';
            document.getElementById('addRoleForm').style.display = 'none';
            document.getElementById('newRoleTxt').innerHTML = 'Temp Role is disabled';
        }
    })
    .catch(err => {
        console.error('Error fetching temp-role-initial-status:', err);
    });

fetch('/temp-role-status' + authParam)
    .then(res => {
        if (!res.ok) {
            console.error('Failed to fetch temp-role-status:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('temp-role-status:', status);
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
    })
    .catch(err => {
        console.error('Error fetching temp-role-status:', err);
    });

fetch('/debug-status' + authParam)
    .then(res => {
        if (!res.ok) {
            console.error('Failed to fetch debug-status:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('debug-status:', status);
        document.getElementById('debugToggle').checked = (status === 'enabled');
    })
    .catch(err => {
        console.error('Error fetching debug-status:', err);
    });

function loadBindings() {
    console.log('Loading bindings...');
    fetch('/bindings-detailed' + authParam)
        .then(res => {
            if (!res.ok) {
                console.error('Failed to fetch bindings-detailed:', res.status, res.statusText);
                throw new Error(`HTTP ${res.status}: ${res.statusText}`);
            }
            return res.json();
        })
        .then(bindings => {
            console.log('Bindings loaded:', bindings.length);
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
        })
        .catch(err => {
            console.error('Error loading bindings:', err);
        });
}

function toggleBinding(userId, channelId, enabled) {
    console.log('Toggling:', userId, channelId, enabled);
    fetch('/toggle-binding' + authParam, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `userId=${userId}&channelId=${channelId}&enabled=${enabled}`
    }).then(loadBindings);
}

function removeBinding(userId, channelId) {
    fetch('/remove-binding' + authParam, {
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
    fetch('/add-binding' + authParam, {
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
    console.log('Loading temp roles...');
    fetch('/temp-roles-detailed' + authParam)
        .then(res => {
            if (!res.ok) {
                console.error('Failed to fetch temp-roles-detailed:', res.status, res.statusText);
                throw new Error(`HTTP ${res.status}: ${res.statusText}`);
            }
            return res.json();
        })
        .then(bindings => {
            console.log('Temp roles loaded:', bindings.length);
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
        })
        .catch(err => {
            console.error('Error loading temp roles:', err);
        });
}
loadTempRoles();

function toggleTempRole(roleId, channelId, enabled) {
    console.log('Toggling:', roleId, channelId, enabled);
    fetch('/toggle-temp-role' + authParam, {
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
    fetch('/add-temp-role' + authParam, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `roleId=${roleId}&channelId=${channelId}`
    }).then(() => {
        form.reset();
        loadTempRoles();
    });
};

function removeTempRole(roleId, channelId) {
    fetch('/remove-temp-role' + authParam, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `roleId=${roleId}&channelId=${channelId}`
    }).then(loadTempRoles);
}

fetch('/get-info')
    .then(res => {
        if (!res.ok) {
            console.error('Failed to fetch get-info:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.json();
    })
    .then(data => {
        console.log('Bot info loaded:', data);
        document.getElementById('fileName').innerHTML= data.fullVersion;
        document.getElementById('lastRestart').innerHTML = data.lastRestart;
    })
    .catch(err => {
        console.error('Error loading bot info:', err);
        document.getElementById('fileName').innerHTML= "Error loading info - " + err.message;
        document.getElementById('lastRestart').innerHTML = "Error loading info - " + err.message;
    });

function toggleWywoz(enabled) {
    console.log('Toggling wywoz:', enabled);
    fetch('/toggle-wywoz' + authParam, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `status=${enabled}`
    })
    .then(res => {
        if (!res.ok) {
            console.error('Failed to toggle wywoz:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('Wywoz toggled to:', status);
        if (status === 'disabled') {
            document.getElementById('bindingsTable').style.display = 'none';
            document.getElementById('addBindingForm').style.display = 'none';
            document.getElementById('newBindingTxt').innerHTML = 'Auto-Kick is disabled';
        } else {
            document.getElementById('bindingsTable').style.display = 'table';
            document.getElementById('addBindingForm').style.display = 'auto';
            document.getElementById('newBindingTxt').innerHTML = 'New Auto-Kick';
        }
    })
    .catch(err => {
        console.error('Error toggling wywoz:', err);
        document.getElementById('wywozSmieciToggle').checked = !enabled;
    });
}

function toggleTempRoleStatus(enabled) {
    console.log('Toggling temp role status:', enabled);
    fetch('/toggle-temp-role-status' + authParam, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `status=${enabled}`
    })
    .then(res => {
        if (!res.ok) {
            console.error('Failed to toggle temp role status:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('Temp role status toggled to:', status);
        if (status === 'disabled') {
            document.getElementById('rolesTable').style.display = 'none';
            document.getElementById('addRoleForm').style.display = 'none';
            document.getElementById('newRoleTxt').innerHTML = 'Temp Role is disabled';
        } else {
            document.getElementById('rolesTable').style.display = 'table';
            document.getElementById('addRoleForm').style.display = 'auto';
            document.getElementById('newRoleTxt').innerHTML = 'New Temporary Role';
        }
    })
    .catch(err => {
        console.error('Error toggling temp role status:', err);
        document.getElementById('tempRoleToggle').checked = !enabled;
    });
}

function toggleDebug(enabled) {
    console.log('Toggling debug:', enabled);
    fetch('/toggle-debug' + authParam, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `status=${enabled}`
    })
    .then(res => {
        if (!res.ok) {
            console.error('Failed to toggle debug:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(status => {
        console.log('Debug toggled to:', status);
    })
    .catch(err => {
        console.error('Error toggling debug:', err);
        document.getElementById('debugToggle').checked = !enabled;
    });
}

function forceRestart() {
    if (!confirm('Are you sure you want to restart the bot?')) {
        return;
    }
    console.log('Force restarting bot...');
    fetch('/force-restart' + authParam, {
        method: 'POST'
    })
    .then(res => {
        if (!res.ok) {
            console.error('Failed to force restart:', res.status, res.statusText);
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        return res.text();
    })
    .then(result => {
        console.log('Bot restart initiated:', result);
        alert('Bot restart initiated. The page will reload in 5 seconds...');
        setTimeout(() => {
            window.location.reload();
        }, 5000);
    })
    .catch(err => {
        console.error('Error restarting bot:', err);
        alert('Error restarting bot: ' + err.message);
    });
}

console.log('botSettings.js execution complete');
