/* 
 * graphs for tone pitch recognition
 */

var
    container = document.getElementById('pitch0'),
    data = [],
    graph_number = 0,
    graph,
    y_min = null; 

(function () {
    container = document.getElementById('pitch1');
    graph_draw();
    container = document.getElementById('pitch0');
    graph_draw();
})();

function graph_draw() {

    if (graph_number == 0) {
        container = document.getElementById('pitch0');
    } else {
        container = document.getElementById('pitch1');
    };

    graph = Flotr.draw(container, [ data ], {
        xaxis: { showLabels: false },
        yaxis: { min: y_min, showLabels: false, scaling: 'logarithmic' },
        lines: { show: true, fill: true},
        grid:  { horizontalLines: false, minorHorizontalLines: false, verticalLines: false, minorVerticalLines: false, outline: ''}
    });
}

/*
 * begin drawing graph. 
 * g is 0 or 1, depending upon whether we are drawing the tones of the question or the answer.
 * y0 is the lowest expected frequency
 */

function graph_start(g, y0) {
    graph_number = g;
    y_min = null;
    if (y0 > 0) y_min = y0;
    data = [];
    graph_draw();
}

/* 
 * Add another data point to the graph. x time in seconds, y frequency in Hz.
 */

function graph_add(x, y) {
    /* frequency -1 means no tone detected */
    if (y == -1) {
        y = null;
        if ((data.length == 0) || (data[data.length-1][1] == null)) return; /* already terminated */
    }

    /* "glitches". XXX fixme */

    /* if tones is higher than 700, assume glitch. */
    if (y > 700) return;

    /* lowest frequency */
    if ((y != null) && (y < y_min)) y_min = y;

    /* add data pair */
    data.push([x, y]);
    graph_draw();
}

/* not truncated */
