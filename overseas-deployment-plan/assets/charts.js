(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var accent3 = style.getPropertyValue('--accent3').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  var chartEl = document.getElementById('chart-cost');
  if (!chartEl) return;

  var chart = echarts.init(chartEl, null, { renderer: 'svg' });

  var categories = [
    'VPS 服务器',
    '托管 MySQL',
    '视频存储与观看\n(Cloudflare Stream)',
    '图片存储\n(Cloudflare R2)',
    'Apple 开发者\n(年摊)',
    'Google Play\n(年摊)',
    '域名\n(年摊)',
    'CDN + SSL\n(Cloudflare)',
    'Redis\n(自建)'
  ];

  var values = [48, 36, 60, 0.15, 8.25, 2, 1, 0, 0];
  var displayValues = values.map(function(v) { return v > 0 ? v : 0; });

  chart.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      appendToBody: true,
      formatter: function(params) {
        var p = params[0];
        var val = p.value;
        if (val === 0) {
          return p.name.replace('\n', ' ') + ': $0 (免费)';
        }
        return p.name.replace('\n', ' ') + ': $' + val.toFixed(2) + '/月';
      }
    },
    grid: { top: 30, left: 200, right: 50, bottom: 30 },
    xAxis: {
      type: 'value',
      axisLabel: {
        color: muted,
        formatter: '${value}'
      },
      axisLine: { lineStyle: { color: rule } },
      splitLine: { lineStyle: { color: rule, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: ink, fontSize: 13 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false },
      inverse: true
    },
    series: [{
      type: 'bar',
      data: displayValues.map(function(v, i) {
        var color = accent;
        if (i === 0) color = accent;
        else if (i === 1) color = accent2;
        else if (i === 2) color = accent3;
        else if (v === 0) color = muted;
        return {
          value: v,
          itemStyle: {
            color: v === 0 ? rule : color,
            borderRadius: [0, 4, 4, 0]
          }
        };
      }),
      barWidth: '55%',
      label: {
        show: true,
        position: 'right',
        color: ink,
        fontSize: 13,
        fontWeight: 600,
        formatter: function(p) {
          if (p.value === 0) return '免费';
          return '$' + p.value.toFixed(2);
        }
      }
    }]
  });

  window.addEventListener('resize', function() { chart.resize(); });
})();
