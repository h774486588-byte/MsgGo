/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package top.yztz.msggo.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Map;

public class TextParserTest {

    @Test
    public void parse_preservesDollarSignFromCell() {
        String result = TextParser.parse("Amount: ${amount}", Map.of("amount", "$100"));

        assertEquals("Amount: $100", result);
    }

    @Test
    public void parse_preservesStandaloneDollarSignFromCell() {
        String result = TextParser.parse("Currency: ${currency}", Map.of("currency", "$"));

        assertEquals("Currency: $", result);
    }

    @Test
    public void parse_preservesBackslashesFromCell() {
        String result = TextParser.parse("Path: ${path}", Map.of("path", "C:\\Users\\demo\\"));

        assertEquals("Path: C:\\Users\\demo\\", result);
    }

    @Test
    public void parse_preservesUnknownVariable() {
        String result = TextParser.parse("Hello ${name}, ${unknown}", Map.of("name", "Alice"));

        assertEquals("Hello Alice, ${unknown}", result);
    }

    @Test
    public void parse_replacesMultipleVariablesWithLiteralSpecialCharacters() {
        String result = TextParser.parse(
                "${name} paid ${amount}; receipt: ${receipt}",
                Map.of(
                        "name", "Alice",
                        "amount", "$12.50",
                        "receipt", "$1\\receipt"
                )
        );

        assertEquals("Alice paid $12.50; receipt: $1\\receipt", result);
    }
}
