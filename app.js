const STORAGE_KEY = 'cycleTrackerData';
let currentDate = new Date();
let periodStartDate = null; // Temporary variable to track the start date of a period

function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

function setCookie(name, value, days = 365) {
    const date = new Date();
    date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
    document.cookie = `${name}=${value};expires=${date.toUTCString()};path=/`;
}

// Safely parse cookie data
let calendarData;

try {
    const cookieData = getCookie(STORAGE_KEY);
    calendarData = cookieData ? JSON.parse(cookieData) : [];
} catch (error) {
    console.error('Error parsing cookie data:', error);
    calendarData = [];
}

function saveData() {
    setCookie(STORAGE_KEY, JSON.stringify(calendarData));
}

function generateCalendar(date) {
    const calendar = document.getElementById('calendar');
    console.log('Calendar element:', calendar); // Debugging
    if (!calendar) {
        console.error('Calendar element not found!');
        return;
    }

    calendar.innerHTML = '';
    
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    
    document.getElementById('currentMonth').textContent = 
        `${date.toLocaleString('default', { month: 'long' })} ${year}`;

    // Add empty cells for days before the first day
    for (let i = 0; i < firstDay.getDay(); i++) {
        const emptyDay = document.createElement('div');
        emptyDay.classList.add('day');
        calendar.appendChild(emptyDay);
    }

    // Create days for the month
    for (let day = 1; day <= lastDay.getDate(); day++) {
        const dateStr = new Date(year, month, day).toISOString().split('T')[0];
        const dayElement = document.createElement('div');
        dayElement.classList.add('day');
        dayElement.textContent = day;
        
        // Check if the date falls within any period
        const isMarked = calendarData.some(period => 
            dateStr >= period.start && dateStr <= period.end
        );

        if (isMarked) {
            dayElement.classList.add('marked');
        }

        // Highlight the start date if it's selected
        if (periodStartDate === dateStr) {
            dayElement.classList.add('selected-start');
        }

        dayElement.addEventListener('click', () => {
            // Handle period selection
            handlePeriodSelection(dateStr, dayElement);
        });

        calendar.appendChild(dayElement);
    }

    console.log('Calendar generated successfully'); // Debugging
}

function handlePeriodSelection(dateStr, dayElement) {
    // Check if the clicked date is part of an existing period
    const periodIndex = calendarData.findIndex(period => 
        dateStr >= period.start && dateStr <= period.end
    );

    if (periodIndex !== -1) {
        // If the date is part of a period, ask the user if they want to delete it
        const period = calendarData[periodIndex];
        const confirmDelete = confirm(`Do you want to delete the period from ${formatDate(new Date(period.start))} to ${formatDate(new Date(period.end))}?`);

        if (confirmDelete) {
            // Remove the period from calendarData
            calendarData.splice(periodIndex, 1);

            // Save data and update the calendar
            saveData();
            generateCalendar(currentDate);
            updatePredictions();
        }
    } else if (!periodStartDate) {
        // If no start date is selected, set the clicked date as the start date
        periodStartDate = dateStr;
        dayElement.classList.add('selected-start'); // Highlight the start date
    } else {
        // If a start date is already selected, set the clicked date as the end date
        const startDate = new Date(periodStartDate);
        const endDate = new Date(dateStr);

        if (endDate < startDate) {
            // If the end date is before the start date, swap them
            [startDate, endDate] = [endDate, startDate];
        }

        // Add the period to calendarData
        calendarData.push({
            start: startDate.toISOString().split('T')[0],
            end: endDate.toISOString().split('T')[0]
        });

        // Reset the start date and remove the highlight
        periodStartDate = null;
        generateCalendar(currentDate);

        // Save data and update the calendar
        saveData();
        updatePredictions();
    }
}

function updatePredictions() {
    const periods = calendarData.sort((a, b) => new Date(a.start) - new Date(b.start));
    const lastPeriod = periods[periods.length - 1];
    const averageCycle = calculateAverageCycle(periods);
    
    document.getElementById('lastPeriod').textContent = 
        lastPeriod ? `${formatDate(new Date(lastPeriod.start))} - ${formatDate(new Date(lastPeriod.end))}` : '-';
    
    document.getElementById('averageCycle').textContent = 
        averageCycle ? `${Math.round(averageCycle)} days` : '-';
    
    document.getElementById('nextPredicted').textContent = 
        lastPeriod && averageCycle ? 
        formatDate(predictNextDate(new Date(lastPeriod.start), averageCycle)) : '-';
}

function calculateAverageCycle(periods) {
    if (periods.length < 2) return null;
    const diffs = [];
    
    for (let i = 1; i < periods.length; i++) {
        const diff = (new Date(periods[i].start) - new Date(periods[i-1].start)) / (86400000);
        diffs.push(diff);
    }
    
    return diffs.reduce((a, b) => a + b, 0) / diffs.length;
}

function predictNextDate(lastStart, averageCycle) {
    const nextDate = new Date(lastStart);
    nextDate.setDate(nextDate.getDate() + Math.round(averageCycle));
    return nextDate;
}

function formatDate(date) {
    return date ? date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : '-';
}

// Add export functionality
document.getElementById('exportData').addEventListener('click', exportToExcel);

function exportToExcel() {
    const data = calendarData.sort((a, b) => new Date(a.start) - new Date(b.start)).map((period, index) => ({
        "Start Date": new Date(period.start).toLocaleDateString('en-US'),
        "End Date": new Date(period.end).toLocaleDateString('en-US'),
        "Cycle Day": index + 1
    }));

    const worksheet = XLSX.utils.json_to_sheet(data);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Cycle Data");
    XLSX.writeFile(workbook, 'cycle_data.xlsx');
}

// Clear All Data functionality
document.getElementById('clearData').addEventListener('click', () => {
    calendarData = []; // Clear the calendarData array
    saveData(); // Save the empty array to the cookie
    generateCalendar(currentDate); // Regenerate the calendar
    updatePredictions(); // Update predictions
});

// Initialization
document.addEventListener('DOMContentLoaded', () => {
    generateCalendar(currentDate);
    updatePredictions();
});