/**
 * Utilidades de interfaz del frontend (Cliente).
 * - Confirmación de acciones destructivas ([data-confirm] en enlaces, formularios y botones).
 * - Filtro en vivo de tablas y listas ([data-filter-input="#idTabla"]).
 * - Filtro por botones ([data-filter-btn] con data-filter-target y data-filter-value).
 * - Exportación CSV de tablas ([data-export-csv="#idTabla"]).
 * - Impresión de la página ([data-print]).
 * - Cierre automático de mensajes flash y estado de carga en formularios.
 */
(function () {
  'use strict';

  // ------------------------------------------------------------------
  // 1. Modal de confirmación reutilizable
  // ------------------------------------------------------------------
  let confirmModal = null;
  let confirmResolve = null;

  function ensureConfirmModal() {
    if (confirmModal) {
      return confirmModal;
    }
    const wrapper = document.createElement('div');
    wrapper.innerHTML =
      '<div class="modal fade" id="appConfirmModal" tabindex="-1" aria-hidden="true">' +
      '  <div class="modal-dialog modal-dialog-centered">' +
      '    <div class="modal-content">' +
      '      <div class="modal-header">' +
      '        <h5 class="modal-title"><i class="bi bi-question-circle me-2"></i>Confirmar acción</h5>' +
      '        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Cerrar"></button>' +
      '      </div>' +
      '      <div class="modal-body"><p class="mb-0" id="appConfirmMessage"></p></div>' +
      '      <div class="modal-footer">' +
      '        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>' +
      '        <button type="button" class="btn btn-danger" id="appConfirmAccept">Confirmar</button>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(wrapper.firstChild);

    const modalEl = document.getElementById('appConfirmModal');
    confirmModal = new bootstrap.Modal(modalEl);
    modalEl.addEventListener('hidden.bs.modal', function () {
      if (confirmResolve) {
        confirmResolve(false);
        confirmResolve = null;
      }
    });
    document.getElementById('appConfirmAccept').addEventListener('click', function () {
      confirmModal.hide();
      if (confirmResolve) {
        confirmResolve(true);
        confirmResolve = null;
      }
    });
    return confirmModal;
  }

  function askConfirm(message) {
    return new Promise(function (resolve) {
      confirmResolve = resolve;
      ensureConfirmModal();
      document.getElementById('appConfirmMessage').textContent = message;
      confirmModal.show();
    });
  }

  document.addEventListener('click', function (event) {
    const trigger = event.target.closest('a[data-confirm]');
    if (!trigger || trigger.dataset.confirmed === 'true') {
      return;
    }
    event.preventDefault();
    askConfirm(trigger.dataset.confirm || '¿Confirmás esta acción?').then(function (ok) {
      if (ok) {
        trigger.dataset.confirmed = 'true';
        trigger.click();
      }
    });
  });

  // Botones de formulario con confirmación (el valor del botón se envía al confirmar)
  document.addEventListener('click', function (event) {
    const button = event.target.closest('button[data-confirm]');
    if (!button || button.dataset.confirmed === 'true') {
      return;
    }
    event.preventDefault();
    askConfirm(button.dataset.confirm || '¿Confirmás esta acción?').then(function (ok) {
      if (ok) {
        button.dataset.confirmed = 'true';
        button.click();
      }
    });
  });

  document.addEventListener('submit', function (event) {
    const form = event.target.closest('form[data-confirm]');
    if (!form || form.dataset.confirmed === 'true') {
      return;
    }
    event.preventDefault();
    askConfirm(form.dataset.confirm || '¿Confirmás esta acción?').then(function (ok) {
      if (ok) {
        form.dataset.confirmed = 'true';
        form.submit();
      }
    });
  });

  // ------------------------------------------------------------------
  // 2. Filtro en vivo de tablas y listas
  // ------------------------------------------------------------------
  function filterTarget(targetSelector) {
    return document.querySelector(targetSelector);
  }

  function filterRows(target, term) {
    const normalized = term.trim().toLowerCase();
    const rows = target.querySelectorAll('[data-filter-item], tbody tr');
    let visible = 0;
    rows.forEach(function (row) {
      const text = row.textContent.toLowerCase();
      const match = normalized === '' || text.indexOf(normalized) !== -1;
      row.classList.toggle('d-none', !match);
      if (match) {
        visible += 1;
      }
    });
    const empty = document.querySelector('[data-filter-empty="' + target.id + '"]');
    if (empty) {
      empty.classList.toggle('d-none', visible !== 0);
    }
  }

  document.querySelectorAll('[data-filter-input]').forEach(function (input) {
    const target = filterTarget(input.dataset.filterInput);
    if (!target) {
      return;
    }
    input.addEventListener('input', function () {
      filterRows(target, input.value);
    });
  });

  document.querySelectorAll('[data-filter-btn]').forEach(function (button) {
    button.addEventListener('click', function () {
      const group = button.dataset.filterGroup || '';
      if (group) {
        document.querySelectorAll('[data-filter-btn][data-filter-group="' + group + '"]').forEach(function (other) {
          other.classList.remove('active');
        });
        button.classList.add('active');
      }
      const target = filterTarget(button.dataset.filterTarget);
      if (!target) {
        return;
      }
      const attr = button.dataset.filterAttr || 'data-filter-tags';
      const value = button.dataset.filterValue || '';
      let visible = 0;
      target.querySelectorAll('[data-filter-item]').forEach(function (item) {
        const tags = (item.getAttribute(attr) || '').toLowerCase();
        const match = value === '' || tags.indexOf(value.toLowerCase()) !== -1;
        item.classList.toggle('d-none', !match);
        if (match) {
          visible += 1;
        }
      });
      const empty = document.querySelector('[data-filter-empty="' + target.id + '"]');
      if (empty) {
        empty.classList.toggle('d-none', visible !== 0);
      }
    });
  });

  // ------------------------------------------------------------------
  // 3. Exportación CSV de tablas
  // ------------------------------------------------------------------
  function tableToCsv(table) {
    const lines = [];
    table.querySelectorAll('tr').forEach(function (row) {
      if (row.classList.contains('d-none')) {
        return;
      }
      const cells = [];
      row.querySelectorAll('th, td').forEach(function (cell) {
        let text = cell.innerText.replace(/\s+/g, ' ').trim();
        text = text.replace(/"/g, '""');
        cells.push('"' + text + '"');
      });
      if (cells.length > 0) {
        lines.push(cells.join(';'));
      }
    });
    return lines.join('\r\n');
  }

  document.querySelectorAll('[data-export-csv]').forEach(function (button) {
    button.addEventListener('click', function () {
      const table = document.querySelector(button.dataset.exportCsv);
      if (!table) {
        return;
      }
      const csv = '\ufeff' + tableToCsv(table);
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = (button.dataset.exportName || 'reporte') + '.csv';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(link.href);
    });
  });

  // ------------------------------------------------------------------
  // 4. Impresión
  // ------------------------------------------------------------------
  document.querySelectorAll('[data-print]').forEach(function (button) {
    button.addEventListener('click', function () {
      window.print();
    });
  });

  // ------------------------------------------------------------------
  // 5. Mensajes flash: cierre automático de éxito/información
  // ------------------------------------------------------------------
  document.querySelectorAll('.app-flash .alert-success, .app-flash .alert-info').forEach(function (alertEl) {
    window.setTimeout(function () {
      if (window.bootstrap && bootstrap.Alert) {
        bootstrap.Alert.getOrCreateInstance(alertEl).close();
      }
    }, 6000);
  });

  // ------------------------------------------------------------------
  // 6. Estado de carga en formularios (data-loading)
  // ------------------------------------------------------------------
  document.querySelectorAll('form[data-loading]').forEach(function (form) {
    form.addEventListener('submit', function () {
      const button = form.querySelector('button[type="submit"]');
      if (button && !button.disabled) {
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Procesando…';
      }
    });
  });
})();
