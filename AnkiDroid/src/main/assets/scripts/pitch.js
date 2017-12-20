/* 
 * graphs for tone pitch recognition
 */

var
    container = document.getElementById('pitch0'),
    data = [],
    x_max = 1,
    graph; 

(function () {
    container = document.getElementById('pitch1');
    graph_draw();
    container = document.getElementById('pitch0');
    graph_draw();
})();

function graph_draw() {
    graph = Flotr.draw(container, [ data ], {
        xaxis: { max: x_max, showLabels: false },
        yaxis: { showLabels: false, scaling: 'logarithmic' },
        lines: { show: true, fill: true},
        grid:  {horizontalLines: false, minorHorizontalLines: false, verticalLines: false, minorVerticalLines: false, outline: ''}
    });
}

/*
 * begin drawing graph. 
 * graph_no is 0 or 1, depending upon whether we are drawing the tones 
 * of the question or the answer. sec_max is the length of the x-axis, in seconds.
 */

function graph_start(graph_no, sec_max) {

    if (graph_no == 0) {
        container = document.getElementById('pitch0');
    } else {
        container = document.getElementById('pitch1');
    };
        
    data = [];
    x_max = 1;
    if (sec_max > x_max) {
        x_max = sec_max;
    }
}

/* 
 * Add another data point to the graph. x time in seconds, y frequency in Hz.
 */

function graph_add(x, y) {
    /* frequency -1 means no tone detected */
    if (y == -1) {
        y = null;
    }
    /* "glitches". XXX fixme */

    /* if tones is higher than 500, assume glitch. */
    if (y > 500) return;

/*
    // if a sample has no tones, but the previous and the next sample have tone, assume glitch.
    if ((data.length >= 3) && (data[data.length - 2][1] == null) && (data[data.length - 1][1] != null) && (data[data.length - 3][1] != null))
      data.splice(data.length - 2, 1);
*/

    /* add data pair */
    data.push([x, y]);
    if (x > x_max) {
        x_max = x;
    }
    graph_draw();
}

/* 
 * End drawing the graph. Postprocessing, if needed.
 */

function graph_stop() {
}

/* not truncated */
