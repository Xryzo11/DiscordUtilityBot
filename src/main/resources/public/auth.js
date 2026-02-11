let authEnabled = false;
let authType = 'none';
let hashedPassword = 'hashedPassword';
let hashedInput = 'hashedInput';
let sessionToken = localStorage.getItem('discordSessionToken');

fetch('/web-auth')
    .then(res => res.json())
    .then(data => {
        authType = data.type || 'none';
        authEnabled = authType !== 'none';

        if (authType === 'password') {
            let password = prompt('Enter WebAuth Password:');
            hashedPassword = data.password;
            hashedInput = sha512(password);
            checkAuth();
        } else if (authType === 'discord') {
            if (sessionToken) {
                validateSession();
            } else {
                initiateDiscordAuth(data);
            }
        } else {
            console.log('WebAuth not enabled, proceeding.');
            authEnabled = false;
        }
    })
    .catch(err => {
        console.error('Error loading web auth config:', err);
        checkAuth();
    });

function validateSession() {
    fetch(`/auth/validate?sessionToken=${encodeURIComponent(sessionToken)}`)
        .then(res => res.json())
        .then(data => {
            if (data.valid) {
                console.log('Discord session valid');
                authEnabled = false;
                logAuthAttempt(true);
            } else {
                console.log('Discord session invalid or expired');
                localStorage.removeItem('discordSessionToken');
                sessionToken = null;
                window.location.reload();
            }
        })
        .catch(err => {
            console.error('Error validating session:', err);
            localStorage.removeItem('discordSessionToken');
            sessionToken = null;
            window.location.reload();
        });
}

function initiateDiscordAuth(data) {
    const clientId = data.clientId;
    const redirectUri = encodeURIComponent(data.redirectUri);
    const scope = 'identify guilds.members.read';

    const authUrl = `https://discord.com/api/oauth2/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code&scope=${scope}`;

    const popup = window.open(authUrl, 'Discord Auth', 'width=500,height=700');

    window.addEventListener('message', function(event) {
        if (event.data && event.data.success !== undefined) {
            popup?.close();

            if (event.data.success && event.data.sessionToken) {
                sessionToken = event.data.sessionToken;
                localStorage.setItem('discordSessionToken', sessionToken);
                authEnabled = false;
                console.log('Discord authentication successful');
                logAuthAttempt(true);
            } else {
                alert('Discord authentication failed: ' + (event.data.error || 'Unknown error'));
                logAuthAttempt(false);
                window.location.href = 'access.html';
                document.write('<h1 style="font-size:100px">Access Denied</h1>');
                document.write('<input type="button" value="Reload" style="background-color: #3498db; color: #fff; width:250px; height:100px; margin:auto; font-size:50px;" onclick="window.location.reload()">');
                window.stop();
                document.execCommand('Stop');
                document.write('<style type="text/undefined">');
            }
        }
    }, { once: true });

    if (!popup || popup.closed || typeof popup.closed === 'undefined') {
        alert('Popup was blocked. Please allow popups for this site and reload.');
    }
}

function logAuthAttempt(success) {
    const currentPage = encodeURIComponent(window.location.pathname);
    const source = authType === 'discord' ? 'discord-oauth' : 'dashboard';
    fetch(`/log-auth?source=${source}&success=${success}&page=${currentPage}`, { method: 'POST' })
        .catch(err => console.error('Failed to log auth:', err));
}

function checkAuth() {
    const currentPage = encodeURIComponent(window.location.pathname);
    if (authEnabled && hashedInput !== hashedPassword) {
        authEnabled = true;
        hashedPassword = 'hashedPassword';
        hashedInput = 'hashedInput';
        logAuthAttempt(false);
        alert('Access denied.');
        window.location.href = 'access.html';
        document.write('<h1 style="font-size:100px">Access Denied</h1>');
        document.write('<input type="button" value="Reload" style="background-color: #3498db; color: #fff; width:250px; height:100px; margin:auto; font-size:50px;" onclick="window.location.reload()">');
        window.stop();
        document.execCommand('Stop');
        document.write('<style type="text/undefined">');
    } else if (authEnabled && hashedInput === hashedPassword) {
        logAuthAttempt(true);
    }
}

function sha512(str) {
    return CryptoJS.SHA512(str).toString();
}

function logout() {
    if (authType === 'discord' && sessionToken) {
        fetch(`/auth/logout?sessionToken=${encodeURIComponent(sessionToken)}`, { method: 'POST' })
            .then(() => {
                localStorage.removeItem('discordSessionToken');
                window.location.reload();
            })
            .catch(err => {
                console.error('Logout error:', err);
                localStorage.removeItem('discordSessionToken');
                window.location.reload();
            });
    }
}
