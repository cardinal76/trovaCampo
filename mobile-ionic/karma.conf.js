/**
 * Launcher Chrome senza sandbox: serve per eseguire i test in container e in
 * CI, dove il browser gira come root. Il resto della configurazione lo
 * fornisce il builder di Angular.
 */
module.exports = function (config) {
  config.set({
    frameworks: ['jasmine'],
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher')],
    browsers: ['ChromeHeadlessCI'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
  });
};
