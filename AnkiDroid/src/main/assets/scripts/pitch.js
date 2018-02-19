/* 
 * graphs for tone pitch recognition
 */

/*
 * begin drawing graph. 
 * g is 0 or 1, depending upon whether we are drawing the tones of the question or the answer.
 * data is an array of [t, f] points where t time in seconds, f frequency in Hz.
 * if f == -1 then no pitch has been detected at time t.
 */

function graph_draw(graph_number, data, pinyin) {

    var 
        ymin = null,
        ymax = null,
        plot_data,
        graph;


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

    /* stylized tone contour derived from pinyin */
    if (pinyin == null) { plot_data = [ data ]; }
    else { 
        /* add stylized tone contour */
        pinyin_data = pinyin2graph(pinyin, ymax);
        /* update min and max */
        for (var i = 0; i < pinyin_data.length; i++) {
            if ((pinyin_data[i][1] != null) && ((ymax == null) || (pinyin_data[i][1] > ymax))) { ymax = pinyin_data[i][1]; }
            if ((pinyin_data[i][1] != null) && ((ymin == null) || (pinyin_data[i][1] < ymin))) { ymin = pinyin_data[i][1]; }
        }
        plot_data = [ data, pinyin_data ];
    }

    /* The pitch range across the four tones of Chinese spans no more than one octave.
       Always draw at least an octave. An octave is 12 semitones.  */

    if (ymin > ymax - 12 ) { ymin = ymax - 12; }

    /* plot */
    graph = Flotr.draw(container, plot_data, {
        xaxis: { showLabels: false },
        yaxis: { min: ymin, max: ymax, showLabels: false },
        lines: { show: true, fill: true},
        grid:  { horizontalLines: false, minorHorizontalLines: false, verticalLines: false, minorVerticalLines: false, outline: ''}
    });
}

/* 
 * converts pinyin into a tone graph.
 * e.g. pinyin2graph("hao3kan1", 450);
 */

function pinyin2graph(pinyin, topline) {
    var pitch_tier = word2tones(pinyin, topline);
    var pitch_data = [];

    for (var i=0; i < pitch_tier.size; ++i) {
        var item = pitch_tier.item(i);
        if (item.value <= 0) {
            item.value = null;
            if ((pitch_data.length == 0) || (pitch_data[pitch_data.length-1][1] == -1)) continue;
            }
        pitch_data.push([item.x, item.value]);
    }

    /* begin at time 0 */
    if (pitch_data.length != 0) {
        begin_time = pitch_data[0][0];
        for (var i=0; i < pitch_data.length; ++i) {
            pitch_data[i][0] = pitch_data[i][0] - begin_time;
        }
    }

    return pitch_data;
}
/* not truncated */
