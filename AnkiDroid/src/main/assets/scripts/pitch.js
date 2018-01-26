/* 
 * graphs for tone pitch recognition
 */

var graph;

(function () {
    graph_draw(0);
    graph_draw(1);
})();

/*
 * begin drawing graph. 
 * g is 0 or 1, depending upon whether we are drawing the tones of the question or the answer.
 * data is an array of [t, f] points where t time in seconds, f frequency in Hz.
 * if f == -1 then no pitch has been detected at time t.
 */

function graph_draw(graph_number, data) {

    var 
        ymin = null,
        ymax = null;

    if (graph_number == 0) {
        container = document.getElementById('pitch0');
    } else {
        container = document.getElementById('pitch1');
    };

    /* determine min and max */
    for (var i = 0; i < data.length; i++) {
        if (data[i][1] == -1) { data[i][1] = null; }
        if ((data[i][1] != null) && ((ymax == null) || (data[i][1] > ymax))) { ymax = data[i][1]; }
        if ((data[i][1] != null) && ((ymin == null) || (data[i][1] < ymin))) { ymin = data[i][1]; }
    }

    /* The pitch range across the four tones spans no more than one octave.
       Always draw at least an octave. */

    if (ymin > ymax / 2) { ymin = ymax / 2; }

    /* plot */
    graph = Flotr.draw(container, [ data ], {
        xaxis: { showLabels: false },
        yaxis: { min: ymin, max: ymax, showLabels: false, scaling: 'logarithmic' },
        lines: { show: true, fill: true},
        grid:  { horizontalLines: false, minorHorizontalLines: false, verticalLines: false, minorVerticalLines: false, outline: ''}
    });
}

/* not truncated */
