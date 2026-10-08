const { defineConfig } = require("cypress");

module.exports = defineConfig({
  e2e: {
    baseUrl: "http://localhost:8080",
    supportFile: "cypress/support/e2e.js",
    video: false,
    defaultCommandTimeout: 8000,
    env: {
      // pausa (ms) após cada ação de interface; 0 desliga o modo lento (ver cypress/support/e2e.js)
      lentidao: 200,
    },
  },
});
