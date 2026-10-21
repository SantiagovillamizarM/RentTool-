//Es un contrato tecnico el cual es una lista de operaciones que da para trabajar con los pagos

//Esta es la ruta que tiene este archivo del proyecto
package com.renttool.application.billing.port.in;

//importa la informacion dentro de "Invoice"
import com.renttool.domain.billing.Invoice;
//importa la informacion dentro de "Payment"
import com.renttool.domain.billing.Payment;
//importa la informacion dentro de "Participant"
import com.renttool.domain.reservation.Participant;
//importa el tipo para manejar dinero sin errores de redondeo
import java.math.BigDecimal;
//importa las funciones de "List"
import java.util.List;

//Esta es la interfaz, es una "Lista de tareas" la cual se debe cumplir llamada "ManagePaymentUseCase"
public interface ManagePaymentsUseCase {
  //Paymente es lo que devuelve y es un objeto, un tipo "Payment"
  //Pay es la accion que hace con los datos que tiene adentro de el
  //El cliente paga al solicitar, con tarjeta o Nequi de prueba (pasarela simulada)
  Payment pay(Participant actor, Long reservationId, Checkout checkout);
  //Sumary es la accion que hace que en español es un "Resumen" y lo hace con los datos que trae con get
  Summary get(Participant actor, Long reservationId);
  //Pide la factura de una reserva. Recibe quién la pide y de qué reserva, y devuelve un objeto Invoice, que es la factura.
  Invoice invoice(Participant actor, Long reservationId);
  //Revisa que el cliente ya haya pagado antes de que el proveedor acepte la reserva
  void ensurePaid(Long reservationId);
  //Si la reserva no sigue adelante (rechazada, vencida o cancelada), devuelve el dinero y anula la factura
  void refundIfPaid(Long reservationId, String reason);

  /**
   * Datos que el cliente escribe en la pasarela simulada. Con TARJETA se usan number, holder,
   * expMonth, expYear y cvv; con NEQUI, phone. No se guardan: solo se valida y se enmascara.
   */
  record Checkout(Method method, String number, String holder, int expMonth, int expYear,
      String cvv, String phone) {
    public enum Method { TARJETA, NEQUI }
  }

  //
  //amount es el total de la reserva, el valor que se cobra en la pasarela
  record Summary(boolean payable, BigDecimal amount, List<Payment> payments, String invoiceNumber) {}
}
