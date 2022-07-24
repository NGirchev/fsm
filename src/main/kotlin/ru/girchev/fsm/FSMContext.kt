package ru.girchev.fsm

/**
 *
 *
 * @author Girchev N.A. <ngirchev@t1-consulting.ru>
 * Date: 26.05.2022
 */
interface FSMContext<STATE> {
    var state: STATE
}
