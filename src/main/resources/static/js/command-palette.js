
document.addEventListener('DOMContentLoaded', () => {
    // 1. Inject Styles
    const style = document.createElement('style');
    style.innerHTML = `
        #cmdPaletteOverlay {
            position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.5); z-index: 9999;
            display: none; align-items: flex-start; justify-content: center;
            padding-top: 100px;
            backdrop-filter: blur(2px);
        }
        #cmdPalette {
            background: white; width: 600px; max-width: 90%;
            border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            overflow: hidden; display: flex; flex-direction: column;
            animation: fadeIn 0.1s ease-out;
        }
        #cmdInput {
            width: 100%; padding: 15px 20px; font-size: 1.2rem;
            border: none; border-bottom: 1px solid #eee; outline: none;
            background: #fafafa;
        }
        #cmdResults {
            max-height: 400px; overflow-y: auto; padding: 10px 0;
        }
        .cmd-item {
            padding: 10px 20px; cursor: pointer; display: flex; justify-content: space-between; align-items: center;
            color: #333; text-decoration: none;
        }
        .cmd-item:hover, .cmd-item.active {
            background: #f0f7ff; color: #0d6efd;
        }
        .cmd-item .cmd-type {
            font-size: 0.75rem; text-transform: uppercase; color: #999;
            border: 1px solid #eee; padding: 2px 6px; border-radius: 4px;
        }
        .cmd-shortcut {
            font-size: 0.8rem; color: #999; margin-left: auto;
        }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
    `;
    document.head.appendChild(style);

    // 2. Inject HTML
    const overlay = document.createElement('div');
    overlay.id = 'cmdPaletteOverlay';
    overlay.innerHTML = `
        <div id="cmdPalette">
            <input type="text" id="cmdInput" placeholder="Type a command or search... (Esc to close)" autocomplete="off">
            <div id="cmdResults"></div>
            <div style="padding: 8px 20px; font-size: 0.8rem; color: #999; border-top: 1px solid #eee; display: flex; justify-content: space-between;">
                <span>Navigate: ↑ ↓</span>
                <span>Select: Enter</span>
            </div>
        </div>
    `;
    document.body.appendChild(overlay);

    const input = document.getElementById('cmdInput');
    const resultsDiv = document.getElementById('cmdResults');

    // 3. Define Commands
    const commands = [
        { title: 'Dashboard', type: 'Page', url: '/' },
        { title: 'Attendance Dashboard', type: 'Page', url: '/attendance' },
        { title: 'Payroll Dashboard', type: 'Page', url: '/payroll' },
        { title: 'App Settings', type: 'Page', url: '/settings' },
        { title: 'Reports: Daily', type: 'Report', url: '/reports/daily' },
        { title: 'Reports: Weekly', type: 'Report', url: '/reports/weekly' },
        { title: 'Reports: Monthly', type: 'Report', url: '/reports/monthly' },
        { title: 'Run Payroll', type: 'Action', url: '/payroll' },

        { title: 'Profile', type: 'User', url: '/profile' },
        { title: 'Logout', type: 'Auth', url: '/logout' }
    ];

    let filtered = [];
    let selectedIndex = 0;

    // 4. Logic
    function openPalette() {
        overlay.style.display = 'flex';
        input.value = '';
        input.focus();
        filterCommands('');
    }

    function closePalette() {
        overlay.style.display = 'none';
    }

    function filterCommands(query) {
        const q = query.toLowerCase();
        filtered = commands.filter(c => c.title.toLowerCase().includes(q));

        // Render
        resultsDiv.innerHTML = '';
        if (filtered.length === 0) {
            resultsDiv.innerHTML = '<div style="padding: 15px 20px; color: #999;">No results found</div>';
            return;
        }

        filtered.forEach((cmd, idx) => {
            const div = document.createElement('div');
            div.className = `cmd-item ${idx === 0 ? 'active' : ''}`;
            div.innerHTML = `
                <div>
                    <i class="fas fa-chevron-right me-2" style="font-size: 0.8rem; opacity: ${idx === 0 ? 1 : 0};"></i>
                    ${cmd.title}
                </div>
                <span class="cmd-type">${cmd.type}</span>
            `;
            div.onclick = () => window.location.href = cmd.url;
            div.onmouseover = () => {
                selectedIndex = idx;
                updateSelection();
            };
            resultsDiv.appendChild(div);
        });
        selectedIndex = 0;
    }

    function updateSelection() {
        const items = resultsDiv.querySelectorAll('.cmd-item');
        items.forEach((item, idx) => {
            if (idx === selectedIndex) {
                item.classList.add('active');
                item.querySelector('.fa-chevron-right').style.opacity = '1';
                item.scrollIntoView({ block: 'nearest' });
            } else {
                item.classList.remove('active');
                item.querySelector('.fa-chevron-right').style.opacity = '0';
            }
        });
    }

    // 5. Event Listeners
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
            e.preventDefault();
            openPalette();
        }
        if (e.key === 'Escape' && overlay.style.display === 'flex') {
            closePalette();
        }
    });

    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) closePalette();
    });

    input.addEventListener('input', (e) => filterCommands(e.target.value));

    input.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            selectedIndex = (selectedIndex + 1) % filtered.length;
            updateSelection();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            selectedIndex = (selectedIndex - 1 + filtered.length) % filtered.length;
            updateSelection();
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (filtered[selectedIndex]) {
                window.location.href = filtered[selectedIndex].url;
            }
        }
    });

    // Check if we have a trigger button in DOM already (optional)
    const triggerBtn = document.getElementById('cmdPaletteTrigger');
    if (triggerBtn) {
        triggerBtn.addEventListener('click', openPalette);
    }
});
