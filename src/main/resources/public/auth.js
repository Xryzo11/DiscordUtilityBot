let authEnabled = false;
let hashedPassword = 'hashedPassword';
let hashedInput = 'hashedInput';
fetch('/web-auth')
    .then(res => res.json())
    .then(data => {
        authEnabled = data.enabled;
        if (data.enabled) {
            let password = prompt('Enter WebAuth Password:');
            hashedPassword = data.password;
            hashedInput = sha512(password);
            checkAuth();
        } else {
            console.log('WebAuth not enabled, proceeding.');
        }
    })
    .catch(err => {
        console.error('Error loading web auth config:', err);
        checkAuth();
    });
function checkAuth() {
    const currentPage = encodeURIComponent(window.location.pathname);
    if (authEnabled && hashedInput !== hashedPassword) {
        authEnabled = true;
        hashedPassword = 'hashedPassword';
        hashedInput = 'hashedInput';
        fetch(`/log-auth?source=dashboard&success=false&page=${currentPage}`, { method: 'POST' }).catch(err => console.error('Failed to log auth:', err));
        alert('Access denied.');
        window.location.href = 'access.html';
        document.write('<h1 style="font-size:100px">Access Denied</h1>');
        document.write('<input type="button" value="Reload" style="background-color: #3498db; color: #fff; width:250px; height:100px; margin:auto; font-size:50px;" onclick="window.location.reload()">');
        window.stop();
        document.execCommand('Stop');
        document.write('<style type="text/undefined">');
    } else if (authEnabled && hashedInput === hashedPassword) {
        fetch(`/log-auth?source=dashboard&success=true&page=${currentPage}`, { method: 'POST' }).catch(err => console.error('Failed to log auth:', err));
    }
}