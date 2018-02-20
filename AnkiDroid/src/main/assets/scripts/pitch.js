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
    var pitch_tier = word2tones(marks2numbers(pinyin), topline);
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

/*
 * Convert pinyin tone marks to tone numbers.
 * Data from github pffy/java-hanyupinyin. 
 */

function marks2numbers(str) {
    pinyin = str.toLowerCase();
    pinyin = pinyin.replace(/u\u0101ng/g, "uang1");
    pinyin = pinyin.replace(/u\u00e1ng/g, "uang2");
    pinyin = pinyin.replace(/u\u0103ng/g, "uang3");
    pinyin = pinyin.replace(/u\u01ceng/g, "uang3");
    pinyin = pinyin.replace(/u\u00e0ng/g, "uang4");
    pinyin = pinyin.replace(/i\u014dng/g, "iong1");
    pinyin = pinyin.replace(/i\u00f3ng/g, "iong2");
    pinyin = pinyin.replace(/i\u014fng/g, "iong3");
    pinyin = pinyin.replace(/i\u01d2ng/g, "iong3");
    pinyin = pinyin.replace(/i\u00f2ng/g, "iong4");
    pinyin = pinyin.replace(/i\u0101ng/g, "iang1");
    pinyin = pinyin.replace(/i\u00e1ng/g, "iang2");
    pinyin = pinyin.replace(/i\u0103ng/g, "iang3");
    pinyin = pinyin.replace(/i\u01ceng/g, "iang3");
    pinyin = pinyin.replace(/i\u00e0ng/g, "iang4");
    pinyin = pinyin.replace(/\u00fcan/g, "uu1an");
    pinyin = pinyin.replace(/\u01d8an/g, "uu2an");
    pinyin = pinyin.replace(/\u01daan/g, "uu3an");
    pinyin = pinyin.replace(/\u01dcan/g, "uu4an");
    pinyin = pinyin.replace(/u\u0101n/g, "uan1");
    pinyin = pinyin.replace(/u\u00e1n/g, "uan2");
    pinyin = pinyin.replace(/u\u0103n/g, "uan3");
    pinyin = pinyin.replace(/u\u01cen/g, "uan3");
    pinyin = pinyin.replace(/u\u00e0n/g, "uan4");
    pinyin = pinyin.replace(/u\u0101i/g, "uai1");
    pinyin = pinyin.replace(/u\u00e1i/g, "uai2");
    pinyin = pinyin.replace(/u\u0103i/g, "uai3");
    pinyin = pinyin.replace(/u\u01cei/g, "uai3");
    pinyin = pinyin.replace(/u\u00e0i/g, "uai4");
    pinyin = pinyin.replace(/\u012bng/g, "ing1");
    pinyin = pinyin.replace(/\u00edng/g, "ing2");
    pinyin = pinyin.replace(/\u012dng/g, "ing3");
    pinyin = pinyin.replace(/\u01d0ng/g, "ing3");
    pinyin = pinyin.replace(/\u00ecng/g, "ing4");
    pinyin = pinyin.replace(/i\u0101n/g, "ian1");
    pinyin = pinyin.replace(/i\u00e1n/g, "ian2");
    pinyin = pinyin.replace(/i\u0103n/g, "ian3");
    pinyin = pinyin.replace(/i\u01cen/g, "ian3");
    pinyin = pinyin.replace(/i\u00e0n/g, "ian4");
    pinyin = pinyin.replace(/i\u0101o/g, "iao1");
    pinyin = pinyin.replace(/i\u00e1o/g, "iao2");
    pinyin = pinyin.replace(/i\u0103o/g, "iao3");
    pinyin = pinyin.replace(/i\u01ceo/g, "iao3");
    pinyin = pinyin.replace(/i\u00e0o/g, "iao4");
    pinyin = pinyin.replace(/\u014dng/g, "ong1");
    pinyin = pinyin.replace(/\u00f3ng/g, "ong2");
    pinyin = pinyin.replace(/\u014fng/g, "ong3");
    pinyin = pinyin.replace(/\u01d2ng/g, "ong3");
    pinyin = pinyin.replace(/\u00f2ng/g, "ong4");
    pinyin = pinyin.replace(/\u0113ng/g, "eng1");
    pinyin = pinyin.replace(/\u00e9ng/g, "eng2");
    pinyin = pinyin.replace(/\u0115ng/g, "eng3");
    pinyin = pinyin.replace(/\u011bng/g, "eng3");
    pinyin = pinyin.replace(/\u00e8ng/g, "eng4");
    pinyin = pinyin.replace(/\u0101ng/g, "ang1");
    pinyin = pinyin.replace(/\u00e1ng/g, "ang2");
    pinyin = pinyin.replace(/\u0103ng/g, "ang3");
    pinyin = pinyin.replace(/\u01ceng/g, "ang3");
    pinyin = pinyin.replace(/\u00e0ng/g, "ang4");
    pinyin = pinyin.replace(/\u00fce/g, "uu1e");
    pinyin = pinyin.replace(/\u01d8e/g, "uu2e");
    pinyin = pinyin.replace(/\u01dae/g, "uu3e");
    pinyin = pinyin.replace(/\u01dce/g, "uu4e");
    pinyin = pinyin.replace(/\u016bn/g, "un1");
    pinyin = pinyin.replace(/\u00fan/g, "un2");
    pinyin = pinyin.replace(/\u016dn/g, "un3");
    pinyin = pinyin.replace(/\u01d4n/g, "un3");
    pinyin = pinyin.replace(/\u00f9n/g, "un4");
    pinyin = pinyin.replace(/u\u012b/g, "ui1");
    pinyin = pinyin.replace(/u\u00ed/g, "ui2");
    pinyin = pinyin.replace(/u\u012d/g, "ui3");
    pinyin = pinyin.replace(/u\u01d0/g, "ui3");
    pinyin = pinyin.replace(/u\u00ec/g, "ui4");
    pinyin = pinyin.replace(/u\u014d/g, "uo1");
    pinyin = pinyin.replace(/u\u00f3/g, "uo2");
    pinyin = pinyin.replace(/u\u014f/g, "uo3");
    pinyin = pinyin.replace(/u\u01d2/g, "uo3");
    pinyin = pinyin.replace(/u\u00f2/g, "uo4");
    pinyin = pinyin.replace(/u\u0101/g, "ua1");
    pinyin = pinyin.replace(/u\u00e1/g, "ua2");
    pinyin = pinyin.replace(/u\u0103/g, "ua3");
    pinyin = pinyin.replace(/u\u01ce/g, "ua3");
    pinyin = pinyin.replace(/u\u00e0/g, "ua4");
    pinyin = pinyin.replace(/\u012bn/g, "in1");
    pinyin = pinyin.replace(/\u00edn/g, "in2");
    pinyin = pinyin.replace(/\u012dn/g, "in3");
    pinyin = pinyin.replace(/\u01d0n/g, "in3");
    pinyin = pinyin.replace(/\u00ecn/g, "in4");
    pinyin = pinyin.replace(/i\u016b/g, "iu1");
    pinyin = pinyin.replace(/i\u00fa/g, "iu2");
    pinyin = pinyin.replace(/i\u016d/g, "iu3");
    pinyin = pinyin.replace(/i\u01d4/g, "iu3");
    pinyin = pinyin.replace(/i\u00f9/g, "iu4");
    pinyin = pinyin.replace(/i\u0113/g, "ie1");
    pinyin = pinyin.replace(/i\u00e9/g, "ie2");
    pinyin = pinyin.replace(/i\u0115/g, "ie3");
    pinyin = pinyin.replace(/i\u011b/g, "ie3");
    pinyin = pinyin.replace(/i\u00e8/g, "ie4");
    pinyin = pinyin.replace(/i\u0101/g, "ia1");
    pinyin = pinyin.replace(/i\u00e1/g, "ia2");
    pinyin = pinyin.replace(/i\u0103/g, "ia3");
    pinyin = pinyin.replace(/i\u01ce/g, "ia3");
    pinyin = pinyin.replace(/i\u00e0/g, "ia4");
    pinyin = pinyin.replace(/\u0113n/g, "en1");
    pinyin = pinyin.replace(/\u00e9n/g, "en2");
    pinyin = pinyin.replace(/\u0115n/g, "en3");
    pinyin = pinyin.replace(/\u011bn/g, "en3");
    pinyin = pinyin.replace(/\u00e8n/g, "en4");
    pinyin = pinyin.replace(/\u0101n/g, "an1");
    pinyin = pinyin.replace(/\u00e1n/g, "an2");
    pinyin = pinyin.replace(/\u0103n/g, "an3");
    pinyin = pinyin.replace(/\u01cen/g, "an3");
    pinyin = pinyin.replace(/\u00e0n/g, "an4");
    pinyin = pinyin.replace(/\u014du/g, "ou1");
    pinyin = pinyin.replace(/\u00f3u/g, "ou2");
    pinyin = pinyin.replace(/\u014fu/g, "ou3");
    pinyin = pinyin.replace(/\u01d2u/g, "ou3");
    pinyin = pinyin.replace(/\u00f2u/g, "ou4");
    pinyin = pinyin.replace(/\u0101o/g, "ao1");
    pinyin = pinyin.replace(/\u00e1o/g, "ao2");
    pinyin = pinyin.replace(/\u0103o/g, "ao3");
    pinyin = pinyin.replace(/\u01ceo/g, "ao3");
    pinyin = pinyin.replace(/\u00e0o/g, "ao4");
    pinyin = pinyin.replace(/\u0113i/g, "ei1");
    pinyin = pinyin.replace(/\u00e9i/g, "ei2");
    pinyin = pinyin.replace(/\u0115i/g, "ei3");
    pinyin = pinyin.replace(/\u011bi/g, "ei3");
    pinyin = pinyin.replace(/\u00e8i/g, "ei4");
    pinyin = pinyin.replace(/\u0101i/g, "ai1");
    pinyin = pinyin.replace(/\u00e1i/g, "ai2");
    pinyin = pinyin.replace(/\u0103i/g, "ai3");
    pinyin = pinyin.replace(/\u01cei/g, "ai3");
    pinyin = pinyin.replace(/\u00e0i/g, "ai4");
    pinyin = pinyin.replace(/\u0113r/g, "er1");
    pinyin = pinyin.replace(/\u00e9r/g, "er2");
    pinyin = pinyin.replace(/\u0115r/g, "er3");
    pinyin = pinyin.replace(/\u011br/g, "er3");
    pinyin = pinyin.replace(/\u00e8r/g, "er4");
    pinyin = pinyin.replace(/\u00fc/g, "uu1");
    pinyin = pinyin.replace(/\u01d8/g, "uu2");
    pinyin = pinyin.replace(/\u01da/g, "uu3");
    pinyin = pinyin.replace(/\u01dc/g, "uu4");
    pinyin = pinyin.replace(/\u0101/g, "a1");
    pinyin = pinyin.replace(/\u00e1/g, "a2");
    pinyin = pinyin.replace(/\u0103/g, "a3");
    pinyin = pinyin.replace(/\u01ce/g, "a3");
    pinyin = pinyin.replace(/\u00e0/g, "a4");
    pinyin = pinyin.replace(/\u0113/g, "e1");
    pinyin = pinyin.replace(/\u00e9/g, "e2");
    pinyin = pinyin.replace(/\u0115/g, "e3");
    pinyin = pinyin.replace(/\u011b/g, "e3");
    pinyin = pinyin.replace(/\u00e8/g, "e4");
    pinyin = pinyin.replace(/\u012b/g, "i1");
    pinyin = pinyin.replace(/\u00ed/g, "i2");
    pinyin = pinyin.replace(/\u012d/g, "i3");
    pinyin = pinyin.replace(/\u01d0/g, "i3");
    pinyin = pinyin.replace(/\u00ec/g, "i4");
    pinyin = pinyin.replace(/\u014d/g, "o1");
    pinyin = pinyin.replace(/\u00f3/g, "o2");
    pinyin = pinyin.replace(/\u014f/g, "o3");
    pinyin = pinyin.replace(/\u01d2/g, "o3");
    pinyin = pinyin.replace(/\u00f2/g, "o4");
    pinyin = pinyin.replace(/\u016b/g, "u1");
    pinyin = pinyin.replace(/\u00fa/g, "u2");
    pinyin = pinyin.replace(/\u016d/g, "u3");
    pinyin = pinyin.replace(/\u01d4/g, "u3");
    pinyin = pinyin.replace(/\u00f9/g, "u4");
    /* console.log ("marks2numbers: " + str + " -> " + pinyin); */
    return pinyin;
}
/* not truncated */
